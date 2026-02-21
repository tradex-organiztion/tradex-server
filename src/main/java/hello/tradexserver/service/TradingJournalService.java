package hello.tradexserver.service;

import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.dto.request.JournalRequest;
import hello.tradexserver.dto.request.JournalStatsFilterRequest;
import hello.tradexserver.dto.response.JournalDetailResponse;
import hello.tradexserver.dto.response.JournalStatsOptionsResponse;
import hello.tradexserver.dto.response.JournalStatsResponse;
import hello.tradexserver.dto.response.JournalSummaryResponse;
import hello.tradexserver.dto.response.OrderResponse;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TradingJournalService {

    private final TradingJournalRepository tradingJournalRepository;
    private final OrderRepository orderRepository;

    /**
     * 매매일지 목록 조회 (포지션 요약 포함, 필터링 + 페이지네이션)
     */
    @Transactional(readOnly = true)
    public Page<JournalSummaryResponse> getList(Long userId, String symbol, PositionSide side,
                                                 PositionStatus positionStatus,
                                                 LocalDate startDate, LocalDate endDate,
                                                 int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<TradingJournal> spec = Specification
                .where((root, query, cb) -> cb.equal(root.get("user").get("id"), userId));

        if (symbol != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("position").get("symbol"), symbol));
        }
        if (side != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("position").get("side"), side));
        }
        if (positionStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("position").get("status"), positionStatus));
        }
        if (startDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startDateTime));
        }
        if (endDate != null) {
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endDateTime));
        }

        return tradingJournalRepository.findAll(spec, pageable).map(JournalSummaryResponse::from);
    }

    /**
     * 매매일지 상세 조회 (포지션 + 오더 목록 + 저널 내용)
     */
    @Transactional(readOnly = true)
    public JournalDetailResponse getDetail(Long userId, Long journalId) {
        TradingJournal journal = tradingJournalRepository.findByIdAndUserId(journalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));

        List<OrderResponse> orders = orderRepository.findByPositionId(journal.getPosition().getId())
                .stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());

        return JournalDetailResponse.from(journal, orders);
    }

    /**
     * 매매일지 내용 수정 (저널 텍스트 필드만)
     */
    public JournalDetailResponse update(Long userId, Long journalId, JournalRequest request) {
        TradingJournal journal = tradingJournalRepository.findByIdAndUserId(journalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));

        journal.update(
                request.getPlannedTargetPrice(), request.getPlannedStopLoss(),
                request.getEntryScenario(), request.getExitReview(),
                request.getIndicators(), request.getTimeframes(), request.getTechnicalAnalyses()
        );

        if (request.getMarketCondition() != null) {
            journal.getPosition().updateMarketCondition(request.getMarketCondition());
        }

        tradingJournalRepository.save(journal);
        log.info("[JournalService] 매매일지 수정 - userId: {}, journalId: {}", userId, journalId);

        List<OrderResponse> orders = orderRepository.findByPositionId(journal.getPosition().getId())
                .stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());

        return JournalDetailResponse.from(journal, orders);
    }

    /**
     * 통계 필터 선택지 조회 (사용자가 입력한 고유값 목록)
     */
    @Transactional(readOnly = true)
    public JournalStatsOptionsResponse getStatsOptions(Long userId) {
        return JournalStatsOptionsResponse.builder()
                .indicators(tradingJournalRepository.findDistinctIndicatorsByUser(userId))
                .timeframes(tradingJournalRepository.findDistinctTimeframesByUser(userId))
                .technicalAnalyses(tradingJournalRepository.findDistinctTechnicalAnalysesByUser(userId))
                .build();
    }

    /**
     * 필터 조합 통계 조회 (선택한 조건 AND 처리, CLOSED 포지션 기준)
     */
    @Transactional(readOnly = true)
    public JournalStatsResponse getStats(Long userId, JournalStatsFilterRequest filter) {
        Object[] row = tradingJournalRepository.getJournalStats(userId, filter);

        long totalTrades = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long winCount    = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long lossCount   = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        BigDecimal avgPnl = row[3] != null ? new BigDecimal(row[3].toString()).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal avgRoi = row[4] != null ? new BigDecimal(row[4].toString()).setScale(2, RoundingMode.HALF_UP) : null;

        BigDecimal winRate = BigDecimal.ZERO;
        if (totalTrades > 0) {
            winRate = BigDecimal.valueOf(winCount)
                    .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return JournalStatsResponse.builder()
                .totalTrades((int) totalTrades)
                .winCount((int) winCount)
                .lossCount((int) lossCount)
                .winRate(winRate)
                .avgPnl(avgPnl)
                .avgRoi(avgRoi)
                .build();
    }

    /**
     * 매매일지 삭제 (연결된 포지션, 오더 cascade)
     */
    public void delete(Long userId, Long journalId) {
        TradingJournal journal = tradingJournalRepository.findByIdAndUserId(journalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));

        tradingJournalRepository.delete(journal);
        log.info("[JournalService] 매매일지 삭제 - userId: {}, journalId: {}", userId, journalId);
    }
}