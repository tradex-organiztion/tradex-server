package hello.tradexserver.openApi.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.openApi.util.BitgetSignatureUtil;
import hello.tradexserver.openApi.webSocket.dto.BitgetPositionData;
import hello.tradexserver.openApi.webSocket.dto.BitgetPositionMessage;
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
public class BitgetWebSocketClient implements ExchangeWebSocketClient {

    // live용 base url
//    private static final String WSS_URL = "wss://ws.bitget.com/v2/ws/private";
    // demo용 base url
    private static final String WSS_URL = "wss://wspap.bitget.com/v2/ws/private";

    private final Long userId;
    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;
    private WebSocketClient wsClient;
    private final ObjectMapper objectMapper;
    private boolean isConnected = false;
    private boolean isAuthenticated = false;
    private PositionListener positionListener;

    private ScheduledExecutorService scheduledExecutor;
    private int reconnectAttempts = 0;
    private boolean shouldReconnect = true;

    public BitgetWebSocketClient(Long userId, String apiKey, String apiSecret, String passphrase) {
        this.userId = userId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.passphrase = passphrase;
        this.objectMapper = new ObjectMapper();
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
    }

    @Override
    public void setPositionListener(PositionListener listener) {
        this.positionListener = listener;
    }

    @Override
    public void connect() {
        try {
            wsClient = new BitgetWebSocketImpl(new URI(WSS_URL));
            wsClient.connect();
            log.info("[Bitget] WebSocket connecting for user: {}", userId);
        } catch (URISyntaxException e) {
            log.error("[Bitget] Invalid WebSocket URI", e);
        }
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;
        if (wsClient != null) {
            wsClient.close();
            isConnected = false;
            isAuthenticated = false;
            log.info("[Bitget] WebSocket disconnected for user: {}", userId);
        }
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdownNow();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        if (reconnectAttempts >= ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS) {
            log.error("[Bitget] 최대 재연결 시도 횟수({}) 도달 - user: {}",
                    ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS, userId);
            return;
        }

        long delay = Math.min(
                ExchangeWebSocketClient.INITIAL_RECONNECT_DELAY_MS * (1L << reconnectAttempts),
                ExchangeWebSocketClient.MAX_RECONNECT_DELAY_MS
        );
        reconnectAttempts++;

        log.info("[Bitget] 재연결 시도 {}/{} 예약 - user: {}, {}ms 후",
                reconnectAttempts, ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS, userId, delay);

        scheduledExecutor.schedule(() -> {
            if (shouldReconnect && !isConnected()) {
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
            log.warn("[Bitget] Cannot subscribe: not authenticated yet - user: {}", userId);
            return;
        }
        try {
            String subscribeMsg = "{\"op\":\"subscribe\",\"args\":[" +
                    "{\"instType\":\"USDT-FUTURES\",\"channel\":\"positions\",\"instId\":\"default\"}" +
                    "]}";
            wsClient.send(subscribeMsg);
            log.info("[Bitget] Position subscription sent for user: {}", userId);
        } catch (Exception e) {
            log.error("[Bitget] Error subscribing to position - user: {}", userId, e);
        }
    }

    private void startPingScheduler() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            if (isConnected && wsClient != null && wsClient.isOpen()) {
                wsClient.send("ping");
            }
        }, 25, 25, TimeUnit.SECONDS);
    }

    // ================ 내부 WebSocket 클래스 ================
    private class BitgetWebSocketImpl extends WebSocketClient {
        public BitgetWebSocketImpl(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            isConnected = true;
            reconnectAttempts = 0;
            log.info("[Bitget] WebSocket opened for user: {}", userId);
            sendLoginMessage();
            startPingScheduler();
        }

        @Override
        public void onMessage(String message) {
            // Bitget pong 응답
            if ("pong".equals(message)) {
                return;
            }

            log.debug("[Bitget] Message received: {}", message);
            try {
                JsonNode jsonNode = objectMapper.readTree(message);

                // 로그인 응답
                if (jsonNode.has("event") && "login".equals(jsonNode.get("event").asText())) {
                    handleLoginResponse(jsonNode);
                    return;
                }

                // 구독 응답
                if (jsonNode.has("event") && "subscribe".equals(jsonNode.get("event").asText())) {
                    handleSubscribeResponse(jsonNode);
                    return;
                }

                // 에러 응답
                if (jsonNode.has("event") && "error".equals(jsonNode.get("event").asText())) {
                    log.error("[Bitget] Error event - user: {}, msg: {}",
                            userId, jsonNode.path("msg").asText());
                    return;
                }

                // 포지션 데이터
                if (jsonNode.has("arg")) {
                    String channel = jsonNode.path("arg").path("channel").asText();
                    if ("positions".equals(channel)) {
                        handlePositionMessage(message);
                        return;
                    }
                }
            } catch (Exception e) {
                log.error("[Bitget] Error processing message - user: {}", userId, e);
            }
        }

        private void handleLoginResponse(JsonNode jsonNode) {
            String code = jsonNode.path("code").asText("0");
            if ("0".equals(code)) {
                isAuthenticated = true;
                log.info("[Bitget] Login successful - user: {}", userId);
                subscribePosition();
            } else {
                log.error("[Bitget] Login failed - user: {}, code: {}, msg: {}",
                        userId, code, jsonNode.path("msg").asText());
            }
        }

        private void handleSubscribeResponse(JsonNode jsonNode) {
            log.info("[Bitget] Subscription successful - user: {}, channel: {}",
                    userId, jsonNode.path("arg").path("channel").asText());
        }

        private void handlePositionMessage(String message) {
            try {
                BitgetPositionMessage posMsg = objectMapper.readValue(message, BitgetPositionMessage.class);
                if (posMsg.getData() == null || posMsg.getData().isEmpty()) return;

                for (BitgetPositionData data : posMsg.getData()) {
                    Position position = convertToPosition(data);

                    if (positionListener != null) {
                        BigDecimal total = parseBigDecimal(data.getTotal());
                        if (total.compareTo(BigDecimal.ZERO) == 0) {
                            positionListener.onPositionClosed(position);
                        } else {
                            positionListener.onPositionUpdate(position);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[Bitget] Error parsing position message - user: {}", userId, e);
            }
        }

        private Position convertToPosition(BitgetPositionData data) {
            PositionSide side;
            BigDecimal totalSize = parseBigDecimal(data.getTotal());

            if ("long".equalsIgnoreCase(data.getHoldSide())) {
                side = PositionSide.LONG;
            } else if ("short".equalsIgnoreCase(data.getHoldSide())) {
                side = PositionSide.SHORT;
            } else {
                // "net" in one-way mode
                side = totalSize.compareTo(BigDecimal.ZERO) >= 0
                        ? PositionSide.LONG : PositionSide.SHORT;
            }

            return Position.builder()
                    .symbol(data.getInstId())
                    .side(side)
                    .avgEntryPrice(parseBigDecimal(data.getAverageOpenPrice()))
                    .currentSize(totalSize.abs())
                    .totalSize(totalSize.abs())
                    .leverage(parseInteger(data.getLeverage()))
                    .realizedPnl(parseBigDecimal(data.getAchievedProfits()))
                    .entryTime(parseTimestamp(data.getCTime()))
                    .status(totalSize.compareTo(BigDecimal.ZERO) == 0
                            ? PositionStatus.CLOSED : PositionStatus.OPEN)
                    .build();
        }

        private void sendLoginMessage() {
            try {
                // Bitget WebSocket은 timestamp를 초 단위로 사용
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                String sign = BitgetSignatureUtil.generateWebSocketSignature(apiSecret, timestamp);

                String loginMsg = String.format(
                        "{\"op\":\"login\",\"args\":[{\"apiKey\":\"%s\",\"passphrase\":\"%s\"," +
                                "\"timestamp\":\"%s\",\"sign\":\"%s\"}]}",
                        apiKey, passphrase, timestamp, sign
                );
                send(loginMsg);
                log.info("[Bitget] Login message sent - user: {}", userId);
            } catch (Exception e) {
                log.error("[Bitget] Error sending login message", e);
            }
        }

        private BigDecimal parseBigDecimal(String value) {
            if (value == null || value.isEmpty() || "0".equals(value)) return BigDecimal.ZERO;
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }

        private Integer parseInteger(String value) {
            if (value == null || value.isEmpty()) return null;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private LocalDateTime parseTimestamp(String timestamp) {
            if (timestamp == null || timestamp.isEmpty()) return null;
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
            log.warn("[Bitget] WebSocket closed - user: {}, code: {}, reason: {}, remote: {}",
                    userId, code, reason, remote);
            scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
            log.error("[Bitget] WebSocket error - user: {}", userId, ex);
            isConnected = false;
        }
    }
}