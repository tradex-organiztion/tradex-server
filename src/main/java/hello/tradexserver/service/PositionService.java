package hello.tradexserver.service;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.User;
import java.math.BigDecimal;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.dto.request.PositionRequest;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PositionService {

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final TradingJournalRepository tradingJournalRepository;
    private final UserRepository userRepository;

    /**
     * 포지션 수동 생성 → TradingJournal 자동 생성
     */
    public PositionResponse create(Long userId, PositionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        Position position = Position.builder()
                .user(user)
                .exchangeName(request.getExchangeName())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .avgEntryPrice(request.getAvgEntryPrice())
                .currentSize(request.getCurrentSize() != null ? request.getCurrentSize() : BigDecimal.ZERO)
                .leverage(request.getLeverage())
                .targetPrice(request.getTargetPrice())
                .stopLossPrice(request.getStopLossPrice())
                .entryTime(request.getEntryTime())
                .status(PositionStatus.OPEN)
                .build();

        positionRepository.save(position);

        TradingJournal journal = TradingJournal.builder()
                .position(position)
                .user(user)
                .build();
        tradingJournalRepository.save(journal);

        log.info("[PositionService] 포지션 수동 생성 - userId: {}, symbol: {}, positionId: {}",
                userId, request.getSymbol(), position.getId());

        return PositionResponse.from(position);
    }

    /**
     * 포지션 수정
     */
    public PositionResponse update(Long userId, Long positionId, PositionRequest request) {
        Position position = positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

        position.update(
                request.getAvgEntryPrice(), request.getAvgExitPrice(),
                request.getCurrentSize(), request.getLeverage(),
                request.getTargetPrice(), request.getStopLossPrice(),
                request.getEntryTime(), request.getExitTime()
        );

        positionRepository.save(position);
        log.info("[PositionService] 포지션 수정 - userId: {}, positionId: {}", userId, positionId);

        return PositionResponse.from(position);
    }

    /**
     * 포지션 삭제 (연결된 TradingJournal, 오더 cascade)
     */
    public void delete(Long userId, Long positionId) {
        Position position = positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

        positionRepository.delete(position);
        log.info("[PositionService] 포지션 삭제 - userId: {}, positionId: {}", userId, positionId);
    }
}