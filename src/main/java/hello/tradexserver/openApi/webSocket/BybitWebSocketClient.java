package hello.tradexserver.openApi.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.openApi.util.BybitSignatureUtil;
import hello.tradexserver.openApi.webSocket.dto.BybitPositionData;
import hello.tradexserver.openApi.webSocket.dto.BybitPositionMessage;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BybitWebSocketClient implements ExchangeWebSocketClient {
    private static final String WSS_URL = "wss://stream-testnet.bybit.com/v5/private";

    private Long userId;
    private String apiKey;
    private String apiSecret;
    private WebSocketClient wsClient;
    private ObjectMapper objectMapper;
    private boolean isConnected = false;
    private boolean isAuthenticated = false;
    private PositionListener positionListener;

    // 재연결 관련 필드
    private ScheduledExecutorService reconnectExecutor;
    private int reconnectAttempts = 0;
    private boolean shouldReconnect = true;

    public BybitWebSocketClient(Long userId, String apiKey, String apiSecret) {
        this.userId = userId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.objectMapper = new ObjectMapper();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void setPositionListener(PositionListener listener) {
        this.positionListener = listener;
    }

    @Override
    public void connect() {
        try {
            wsClient = new BybitWebSocketImpl(new URI(WSS_URL));
            wsClient.connect();
            log.info("Bybit WebSocket connecting for user: {}", userId);
        } catch (URISyntaxException e) {
            log.error("Invalid WebSocket URI", e);
        }
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;  // 수동 종료 시 재연결 방지
        if (wsClient != null) {
            wsClient.close();
            isConnected = false;
            isAuthenticated = false;
            log.info("Bybit WebSocket disconnected for user: {}", userId);
        }
        shutdownReconnectExecutor();
    }

    private void shutdownReconnectExecutor() {
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdownNow();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) {
            log.info("Reconnection disabled for user: {}", userId);
            return;
        }

        if (reconnectAttempts >= ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnect attempts ({}) reached for user: {}. Giving up.",
                    ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS, userId);
            return;
        }

        // 지수 백오프 계산: 1초, 2초, 4초, 8초... 최대 60초
        long delay = Math.min(
                ExchangeWebSocketClient.INITIAL_RECONNECT_DELAY_MS * (1L << reconnectAttempts),
                ExchangeWebSocketClient.MAX_RECONNECT_DELAY_MS
        );
        reconnectAttempts++;

        log.info("Scheduling reconnect attempt {}/{} for user: {} in {}ms",
                reconnectAttempts, ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS, userId, delay);

        reconnectExecutor.schedule(() -> {
            if (shouldReconnect && !isConnected()) {
                log.info("Attempting to reconnect for user: {} (attempt {}/{})",
                        userId, reconnectAttempts, ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS);
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isConnected() {
        return isConnected && wsClient != null && wsClient.isOpen();
    }

    @Override
    public void subscribePosition() {
        if (!isAuthenticated) {
            log.warn("Cannot subscribe to position: not authenticated yet for user: {}", userId);
            return;
        }
        try {
            String subscribeMsg = "{\"op\":\"subscribe\",\"args\":[\"position\"]}";
            wsClient.send(subscribeMsg);
            log.info("Position subscription sent for user: {}", userId);
        } catch (Exception e) {
            log.error("Error subscribing to position for user: {}", userId, e);
        }
    }

    // ================ 내부 WebSocket 클래스 ================
    private class BybitWebSocketImpl extends WebSocketClient {
        public BybitWebSocketImpl(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            isConnected = true;
            reconnectAttempts = 0;  // 연결 성공 시 재연결 카운터 리셋
            log.info("✅ Bybit WebSocket opened for user: {}", userId);
            sendAuthMessage();
        }

        @Override
        public void onMessage(String message) {
            log.debug("Message received: {}", message);
            try {
                JsonNode jsonNode = objectMapper.readTree(message);

                // 인증 응답 처리
                if (jsonNode.has("op") && "auth".equals(jsonNode.get("op").asText())) {
                    handleAuthResponse(jsonNode);
                    return;
                }

                // 구독 응답 처리
                if (jsonNode.has("op") && "subscribe".equals(jsonNode.get("op").asText())) {
                    handleSubscribeResponse(jsonNode);
                    return;
                }

                // Position 데이터 처리
                if (jsonNode.has("topic") && jsonNode.get("topic").asText().startsWith("position")) {
                    handlePositionMessage(message);
                    return;
                }

                // Ping/Pong 처리
                if (jsonNode.has("op") && "pong".equals(jsonNode.get("op").asText())) {
                    log.debug("Pong received for user: {}", userId);
                    return;
                }

            } catch (Exception e) {
                log.error("Error processing message for user: {}", userId, e);
            }
        }

        private void handleAuthResponse(JsonNode jsonNode) {
            boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
            if (success) {
                isAuthenticated = true;
                log.info("Authentication successful for user: {}", userId);
                // 인증 성공 후 자동으로 position 구독
                subscribePosition();
            } else {
                String retMsg = jsonNode.has("ret_msg") ? jsonNode.get("ret_msg").asText() : "Unknown error";
                log.error("Authentication failed for user: {} - {}", userId, retMsg);
            }
        }

        private void handleSubscribeResponse(JsonNode jsonNode) {
            boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
            if (success) {
                log.info("Subscription successful for user: {}", userId);
            } else {
                String retMsg = jsonNode.has("ret_msg") ? jsonNode.get("ret_msg").asText() : "Unknown error";
                log.error("Subscription failed for user: {} - {}", userId, retMsg);
            }
        }

        private void handlePositionMessage(String message) {
            try {
                BybitPositionMessage positionMessage = objectMapper.readValue(message, BybitPositionMessage.class);

                if (positionMessage.getData() == null || positionMessage.getData().isEmpty()) {
                    return;
                }

                for (BybitPositionData data : positionMessage.getData()) {
                    Position position = convertToPosition(data);

                    if (positionListener != null) {
                        // size가 0이면 포지션이 닫힌 것
                        if ("0".equals(data.getSize()) || data.getSize() == null || data.getSize().isEmpty()) {
                            positionListener.onPositionClosed(position);
                        } else {
                            positionListener.onPositionUpdate(position);
                        }
                    }
                }

                log.info("Position update processed for user: {}, symbol count: {}",
                        userId, positionMessage.getData().size());

            } catch (Exception e) {
                log.error("Error parsing position message for user: {}", userId, e);
            }
        }

        private Position convertToPosition(BybitPositionData data) {
            return Position.builder()
                    .symbol(data.getSymbol())
                    .side(convertSide(data.getSide()))
                    .avgEntryPrice(parseBigDecimal(data.getEntryPrice()))
                    .closedSize(parseBigDecimal(data.getSize()))
                    .leverage(parseInteger(data.getLeverage()))
                    .targetPrice(parseBigDecimal(data.getTakeProfit()))
                    .stopLossPrice(parseBigDecimal(data.getStopLoss()))
                    .realizedPnl(parseBigDecimal(data.getCurRealisedPnl()))
                    .entryTime(parseTimestamp(data.getCreatedTime()))
                    .status(convertStatus(data.getSize()))
                    .build();
        }

        private PositionSide convertSide(String side) {
            if (side == null || side.isEmpty()) {
                return null;
            }
            return "Buy".equalsIgnoreCase(side) ? PositionSide.LONG : PositionSide.SHORT;
        }

        private PositionStatus convertStatus(String size) {
            if (size == null || size.isEmpty() || "0".equals(size)) {
                return PositionStatus.CLOSED;
            }
            return PositionStatus.OPEN;
        }

        private BigDecimal parseBigDecimal(String value) {
            if (value == null || value.isEmpty() || "0".equals(value)) {
                return BigDecimal.ZERO;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }

        private Integer parseInteger(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private LocalDateTime parseTimestamp(String timestamp) {
            if (timestamp == null || timestamp.isEmpty()) {
                return null;
            }
            try {
                long millis = Long.parseLong(timestamp);
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isConnected = false;
            isAuthenticated = false;
            log.warn("Bybit WebSocket closed for user: {} - Code: {}, Reason: {}, Remote: {}",
                    userId, code, reason, remote);

            // 자동 재연결 시도
            scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
            log.error("❌ Bybit WebSocket error for user: {}", userId, ex);
            isConnected = false;
        }

        private void sendAuthMessage() {
            try {
                // expires = 현재 시간 + 10초
                String expires = String.valueOf(System.currentTimeMillis() + 10000);
                String sign = BybitSignatureUtil.generateSignature(apiSecret, expires);

                String authMsg = String.format(
                        "{\"op\":\"auth\",\"args\":[\"%s\",%s,\"%s\"]}",
                        apiKey, expires, sign
                );
                send(authMsg);
                log.info("Auth message sent for user: {}", userId);
            } catch (Exception e) {
                log.error("Error sending auth message", e);
            }
        }
    }
}
