package hello.tradexserver.service;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.openApi.webSocket.PositionListener;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionTrackingService implements PositionListener {

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public void onPositionUpdate(Position wsPosition) {
        log.info("[PositionTracking] Position Update - symbol: {}, side: {}, size: {}, entryPrice: {}",
                wsPosition.getSymbol(), wsPosition.getSide(),
                wsPosition.getCurrentSize(), wsPosition.getAvgEntryPrice());

        // WS에서 받은 포지션은 userId, exchangeName이 없으므로
        // 실제 운영에서는 WebSocket 연결 시 userId/exchangeName을 함께 전달하는 구조 필요
        // 현재는 로그 기록 + 향후 확장 포인트
        // TODO: userId, exchangeName을 Position에 포함하여 DB 조회 및 저장
    }

    @Override
    @Transactional
    public void onPositionClosed(Position wsPosition) {
        log.info("[PositionTracking] Position Closed - symbol: {}, side: {}, pnl: {}",
                wsPosition.getSymbol(), wsPosition.getSide(), wsPosition.getRealizedPnl());

        // TODO: DB에서 해당 OPEN 포지션 조회 → CLOSED로 변경
        // TODO: REST API로 최종 체결 내역 조회 → 평균 청산가 계산
    }
}