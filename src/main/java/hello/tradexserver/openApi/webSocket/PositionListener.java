package hello.tradexserver.openApi.webSocket;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Position;

public interface PositionListener {
    void onPositionUpdate(Position position);
    void onPositionClosed(Position position);

    /**
     * WebSocket 재연결 시 호출 - REST API로 포지션 상태 보완
     * Binance/Bitget 등 미구현 거래소는 default로 무시
     */
    default void onReconnected(ExchangeApiKey apiKey) {}
}
