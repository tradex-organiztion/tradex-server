package hello.tradexserver.openApi.webSocket;

public interface ExchangeWebSocketClient {
    void connect();
    void disconnect();
    boolean isConnected();
}
