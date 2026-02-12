package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.dto.request.JournalRequest;
import hello.tradexserver.dto.response.JournalDetailResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 매매일지 목록 조회 (포지션 요약 포함, 페이지네이션)
     */
    @Transactional(readOnly = true)
    public Page<JournalSummaryResponse> getList(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return tradingJournalRepository.findByUserId(userId, pageable)
                .map(JournalSummaryResponse::from);
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
                request.getEntryScenario(), request.getExitReview()
        );

        tradingJournalRepository.save(journal);
        log.info("[JournalService] 매매일지 수정 - userId: {}, journalId: {}", userId, journalId);

        List<OrderResponse> orders = orderRepository.findByPositionId(journal.getPosition().getId())
                .stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());

        return JournalDetailResponse.from(journal, orders);
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