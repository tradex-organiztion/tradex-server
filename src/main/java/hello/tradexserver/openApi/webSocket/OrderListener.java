package hello.tradexserver.openApi.webSocket;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;

import java.time.LocalDateTime;

public interface OrderListener {

    /**
     * WebSocket으로 Order 메시지 수신 시 호출
     */
    void onOrderReceived(Order order);

    /**
     * WebSocket 재연결 시 끊겼던 구간의 Order를 REST로 보완 조회하도록 트리거
     *
     * @param apiKey        재연결된 API Key
     * @param gapStartTime  끊겼던 시점 (이 시간 이후 Order를 조회해야 함)
     */
    void onReconnected(ExchangeApiKey apiKey, LocalDateTime gapStartTime);
}