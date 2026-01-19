package hello.tradexserver.openApi.webSocket;

import hello.tradexserver.domain.ExchangeApiKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ExchangeWebSocketManager {

    private final Map<String, ExchangeWebSocketClient> activeConnections = new ConcurrentHashMap<>();

    /**
     * WebSocket 연결 시작
     */
    public void connectUser(Long userId, ExchangeApiKey apiKey) {
        String webSocketKey = generateWebSocketKey(userId, apiKey.getExchangeName().name());

        // 이미 연결된 경우 먼저 종료
        if (activeConnections.containsKey(webSocketKey)) {
            disconnectUser(userId, apiKey.getExchangeName().name());
        }

        try {
            ExchangeWebSocketClient client = createWebSocketClient(userId, apiKey);
            client.connect();
            activeConnections.put(webSocketKey, client);

            log.info("✅ WebSocket connected - User: {}, Exchange: {}", userId, apiKey.getExchangeName());
        } catch (Exception e) {
            log.error("❌ Failed to connect WebSocket - User: {}, Exchange: {}",
                    userId, apiKey.getExchangeName(), e);
        }
    }

    /**
     * WebSocket 연결 해제
     */
    public void disconnectUser(Long userId, String exchange) {
        String webSocketKey = generateWebSocketKey(userId, exchange);
        ExchangeWebSocketClient client = activeConnections.remove(webSocketKey);

        if (client != null) {
            client.disconnect();
            log.info("✅ WebSocket disconnected - User: {}, Exchange: {}", userId, exchange);
        }
    }

    /**
     * 연결 상태 확인
     */
    public boolean isConnected(Long userId, String exchange) {
        String webSocketKey = generateWebSocketKey(userId, exchange);
        ExchangeWebSocketClient client = activeConnections.get(webSocketKey);
        return client != null && client.isConnected();
    }

    /**
     * 서버 종료 시 모든 연결 닫기
     */
    public void shutdown() {
        log.info("Shutting down all WebSocket connections");
        activeConnections.values().forEach(ExchangeWebSocketClient::disconnect);
        activeConnections.clear();
    }

    /**
     * 거래소별 WebSocket 클라이언트 생성
     */
    private ExchangeWebSocketClient createWebSocketClient(Long userId, ExchangeApiKey apiKey) {
        String exchange = apiKey.getExchangeName().name();

        if ("bybit".equalsIgnoreCase(exchange)) {
            return new BybitWebSocketClient(
                    userId,
                    apiKey.getApiKey(),
                    apiKey.getApiSecret()
            );
        }

        throw new IllegalArgumentException("Unsupported exchange: " + exchange);
    }

    /**
     * WebSocket 키 생성: {userId}_{exchange}
     */
    private String generateWebSocketKey(Long userId, String exchange) {
        return userId + "_" + exchange.toLowerCase();
    }
}