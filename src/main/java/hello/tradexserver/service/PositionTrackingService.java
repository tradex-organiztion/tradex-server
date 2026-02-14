package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Position;
import hello.tradexserver.openApi.webSocket.PositionListener;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionTrackingService implements PositionListener {

    private final PositionRepository positionRepository;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;

    /**
     * 보조 역할: 기존 OPEN 포지션의 leverage, TP/SL만 업데이트.
     * 포지션 생성/종료는 PositionReconstructionService가 오더 기반으로 처리.
     */
    @Override
    @Transactional
    public void onPositionUpdate(Position wsPosition) {
        ExchangeApiKey apiKey = wsPosition.getExchangeApiKey();
        if (apiKey == null) return;

        ExchangeApiKey freshApiKey = exchangeApiKeyRepository.findById(apiKey.getId())
                .orElse(null);
        if (freshApiKey == null) return;

        Optional<Position> existingOpt = positionRepository.findOpenPositionByApiKey(
                freshApiKey.getId(), wsPosition.getSymbol(), wsPosition.getSide()
        );

        if (existingOpt.isPresent()) {
            Position existing = existingOpt.get();
            existing.updateLeverage(wsPosition.getLeverage());
            existing.updateTargetPrice(wsPosition.getTargetPrice());
            existing.updateStopLossPrice(wsPosition.getStopLossPrice());
            positionRepository.save(existing);
            log.debug("[PositionTracking] 보조 업데이트 - id: {}, symbol: {}, leverage: {}",
                    existing.getId(), existing.getSymbol(), wsPosition.getLeverage());
        } else {
            log.debug("[PositionTracking] OPEN 포지션 없음, skip - symbol: {}, side: {}",
                    wsPosition.getSymbol(), wsPosition.getSide());
        }
    }

    /**
     * 포지션 종료는 PositionReconstructionService가 오더 기반으로 처리.
     * WS 포지션 종료 이벤트는 로그만 기록.
     */
    @Override
    @Transactional
    public void onPositionClosed(Position wsPosition) {
        log.info("[PositionTracking] Position Closed 이벤트 수신 (오더 기반 처리) - symbol: {}, side: {}",
                wsPosition.getSymbol(), wsPosition.getSide());
    }

    // onReconnected: interface default no-op 사용
    // 재연결 보완 로직은 WebSocketOrderService.onReconnected()에서 통합 처리
}
