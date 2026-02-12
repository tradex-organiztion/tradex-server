package hello.tradexserver.openApi.webSocket;

public interface ExchangeWebSocketClient {

    // 재연결 관련 상수
    int MAX_RECONNECT_ATTEMPTS = 10;
    long INITIAL_RECONNECT_DELAY_MS = 1000;   // 1초
    long MAX_RECONNECT_DELAY_MS = 60000;      // 60초

    void connect();
    void disconnect();
    boolean isConnected();
    void subscribePosition();
    void setPositionListener(PositionListener listener);

    default void setOrderListener(OrderListener listener) {
        // 기본 구현: 거래소별 구현체에서 필요 시 오버라이드
    }
}
