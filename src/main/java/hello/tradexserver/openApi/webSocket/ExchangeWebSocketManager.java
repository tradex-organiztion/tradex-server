package hello.tradexserver.openApi.webSocket;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.BinanceRestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeWebSocketManager {

    private final Map<String, ExchangeWebSocketClient> activeConnections = new ConcurrentHashMap<>();

    private final BinanceRestClient binanceRestClient;
    private final PositionListener positionListener;
    private final OrderListener orderListener;

    public void connectUser(Long userId, ExchangeApiKey apiKey) {
        String webSocketKey = generateWebSocketKey(userId, apiKey.getExchangeName().name());

        if (activeConnections.containsKey(webSocketKey)) {
            disconnectUser(userId, apiKey.getExchangeName().name());
        }

        try {
            ExchangeWebSocketClient client = createWebSocketClient(userId, apiKey);

            if (positionListener != null) {
                client.setPositionListener(positionListener);
            }
            if (orderListener != null) {
                client.setOrderListener(orderListener);
            }

            client.connect();
            activeConnections.put(webSocketKey, client);

            log.info("WebSocket connected - User: {}, Exchange: {}", userId, apiKey.getExchangeName());
        } catch (Exception e) {
            log.error("Failed to connect WebSocket - User: {}, Exchange: {}",
                    userId, apiKey.getExchangeName(), e);
        }
    }

    public void disconnectUser(Long userId, String exchange) {
        String webSocketKey = generateWebSocketKey(userId, exchange);
        ExchangeWebSocketClient client = activeConnections.remove(webSocketKey);

        if (client != null) {
            client.disconnect();
            log.info("WebSocket disconnected - User: {}, Exchange: {}", userId, exchange);
        }
    }

    public boolean isConnected(Long userId, String exchange) {
        String webSocketKey = generateWebSocketKey(userId, exchange);
        ExchangeWebSocketClient client = activeConnections.get(webSocketKey);
        return client != null && client.isConnected();
    }

    public void shutdown() {
        log.info("Shutting down all WebSocket connections");
        activeConnections.values().forEach(ExchangeWebSocketClient::disconnect);
        activeConnections.clear();
    }

    private ExchangeWebSocketClient createWebSocketClient(Long userId, ExchangeApiKey apiKey) {
        String exchange = apiKey.getExchangeName().name();

        if ("BYBIT".equalsIgnoreCase(exchange)) {
            return new BybitWebSocketClient(userId, apiKey);
        }

        if ("BINANCE".equalsIgnoreCase(exchange)) {
            return new BinanceWebSocketClient(userId, apiKey, binanceRestClient);
        }

        if ("BITGET".equalsIgnoreCase(exchange)) {
            return new BitgetWebSocketClient(userId, apiKey);
        }

        throw new IllegalArgumentException("Unsupported exchange: " + exchange);
    }

    private String generateWebSocketKey(Long userId, String exchange) {
        return userId + "_" + exchange.toLowerCase();
    }
}