package hello.tradexserver.openApi.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.*;
import hello.tradexserver.openApi.util.BybitSignatureUtil;
import hello.tradexserver.openApi.webSocket.dto.BybitOrderData;
import hello.tradexserver.openApi.webSocket.dto.BybitOrderMessage;
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
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class BybitWebSocketClient implements ExchangeWebSocketClient {
    private static final String WSS_URL = "wss://stream-demo.bybit.com/v5/private";

    private Long userId;
    private ExchangeApiKey exchangeApiKey;
    private String apiKey;
    private String apiSecret;
    private WebSocketClient wsClient;
    private ObjectMapper objectMapper;
    private boolean isConnected = false;
    private boolean isAuthenticated = false;
    private PositionListener positionListener;
    private OrderListener orderListener;

    // 끊긴 시간 추적 - 재연결 시 Gap 보완에 사용
    private final AtomicReference<LocalDateTime> disconnectTime = new AtomicReference<>(null);

    private ScheduledExecutorService reconnectExecutor;
    private int reconnectAttempts = 0;
    private boolean shouldReconnect = true;

    public BybitWebSocketClient(Long userId, ExchangeApiKey exchangeApiKey) {
        this.userId = userId;
        this.exchangeApiKey = exchangeApiKey;
        this.apiKey = exchangeApiKey.getApiKey();
        this.apiSecret = exchangeApiKey.getApiSecret();
        this.objectMapper = new ObjectMapper();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void setPositionListener(PositionListener listener) {
        this.positionListener = listener;
    }

    @Override
    public void setOrderListener(OrderListener listener) {
        this.orderListener = listener;
    }

    @Override
    public void connect() {
        try {
            wsClient = new BybitWebSocketImpl(new URI(WSS_URL));
            wsClient.connect();
            log.info("[Bybit] WebSocket connecting for user: {}", userId);
        } catch (URISyntaxException e) {
            log.error("[Bybit] Invalid WebSocket URI", e);
        }
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;
        if (wsClient != null) {
            wsClient.close();
            isConnected = false;
            isAuthenticated = false;
            log.info("[Bybit] WebSocket disconnected for user: {}", userId);
        }
        shutdownReconnectExecutor();
    }

    private void shutdownReconnectExecutor() {
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdownNow();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        if (reconnectAttempts >= ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS) {
            log.error("[Bybit] 최대 재연결 시도 횟수({}) 도달 - user: {}",
                    ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS, userId);
            return;
        }

        long delay = Math.min(
                ExchangeWebSocketClient.INITIAL_RECONNECT_DELAY_MS * (1L << reconnectAttempts),
                ExchangeWebSocketClient.MAX_RECONNECT_DELAY_MS
        );
        reconnectAttempts++;

        log.info("[Bybit] 재연결 시도 {}/{} 예약 - user: {}, {}ms 후",
                reconnectAttempts, ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS, userId, delay);

        reconnectExecutor.schedule(() -> {
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
            log.warn("[Bybit] Cannot subscribe: not authenticated yet - user: {}", userId);
            return;
        }
        try {
            String subscribeMsg = "{\"op\":\"subscribe\",\"args\":[\"position\"]}";
            wsClient.send(subscribeMsg);
            log.info("[Bybit] Position subscription sent for user: {}", userId);
        } catch (Exception e) {
            log.error("[Bybit] Error subscribing to position - user: {}", userId, e);
        }
    }

    public void subscribeOrders() {
        if (!isAuthenticated) return;
        try {
            String subscribeMsg = "{\"op\":\"subscribe\",\"args\":[\"order\"]}";
            wsClient.send(subscribeMsg);
            log.info("[Bybit] Order subscription sent for user: {}", userId);
        } catch (Exception e) {
            log.error("[Bybit] Error subscribing to order - user: {}", userId, e);
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
            reconnectAttempts = 0;
            log.info("[Bybit] WebSocket opened for user: {}", userId);
            sendAuthMessage();
        }

        @Override
        public void onMessage(String message) {
            log.debug("[Bybit] Message received: {}", message);
            try {
                JsonNode jsonNode = objectMapper.readTree(message);

                if (jsonNode.has("op") && "auth".equals(jsonNode.get("op").asText())) {
                    handleAuthResponse(jsonNode);
                    return;
                }
                if (jsonNode.has("op") && "subscribe".equals(jsonNode.get("op").asText())) {
                    handleSubscribeResponse(jsonNode);
                    return;
                }
                if (jsonNode.has("topic") && jsonNode.get("topic").asText().startsWith("position")) {
                    log.info("[Bybit] position message received: {}", message);
                    handlePositionMessage(message);
                    return;
                }
                if (jsonNode.has("topic") && jsonNode.get("topic").asText().startsWith("order")) {
                    log.info("[Bybit] order message received: {}", message);
                    handleOrderMessage(message);
                    return;
                }
                if (jsonNode.has("op") && "pong".equals(jsonNode.get("op").asText())) {
                    return;
                }
            } catch (Exception e) {
                log.error("[Bybit] Error processing message - user: {}", userId, e);
            }
        }

        private void handleAuthResponse(JsonNode jsonNode) {
            boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
            if (success) {
                isAuthenticated = true;
                log.info("[Bybit] Authentication successful - user: {}", userId);
                subscribePosition();
                subscribeOrders();

                // 연결/재연결 시 Gap 보완 (첫 연결 시 gapStart=null)
                LocalDateTime gapStart = disconnectTime.getAndSet(null);
                if (gapStart != null) {
                    log.info("[Bybit] 재연결 감지 - Gap 보완 시작 (gapStart: {}) - user: {}", gapStart, userId);
                } else {
                    log.info("[Bybit] 첫 연결 - 초기 보완 시작 - user: {}", userId);
                }
                if (orderListener != null) {
                    orderListener.onReconnected(exchangeApiKey, gapStart);
                }
                if (positionListener != null) {
                    positionListener.onReconnected(exchangeApiKey);
                }
            } else {
                String retMsg = jsonNode.has("ret_msg") ? jsonNode.get("ret_msg").asText() : "Unknown error";
                log.error("[Bybit] Authentication failed - user: {} - {}", userId, retMsg);
            }
        }

        private void handleSubscribeResponse(JsonNode jsonNode) {
            boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
            if (success) {
                log.info("[Bybit] Subscription successful - user: {}", userId);
            } else {
                String retMsg = jsonNode.has("ret_msg") ? jsonNode.get("ret_msg").asText() : "Unknown error";
                log.error("[Bybit] Subscription failed - user: {} - {}", userId, retMsg);
            }
        }

        private void handlePositionMessage(String message) {
            try {
                BybitPositionMessage positionMessage = objectMapper.readValue(message, BybitPositionMessage.class);
                if (positionMessage.getData() == null || positionMessage.getData().isEmpty()) return;

                for (BybitPositionData data : positionMessage.getData()) {
                    Position position = convertToPosition(data);

                    if (positionListener != null) {
                        if ("0".equals(data.getSize()) || data.getSize() == null || data.getSize().isEmpty()) {
                            positionListener.onPositionClosed(position);
                        } else {
                            positionListener.onPositionUpdate(position);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[Bybit] Error parsing position message - user: {}", userId, e);
            }
        }

        private void handleOrderMessage(String message) {
            try {
                BybitOrderMessage orderMessage = objectMapper.readValue(message, BybitOrderMessage.class);
                if (orderMessage.getData() == null || orderMessage.getData().isEmpty()) return;

                for (BybitOrderData data : orderMessage.getData()) {
                    String os = data.getOrderStatus();
                    if (!"Filled".equals(os) && !"Cancelled".equals(os)) continue;

                    if (orderListener != null) {
                        orderListener.onOrderReceived(convertToOrder(data));
                    }
                }
            } catch (Exception e) {
                log.error("[Bybit] Error parsing order message - user: {}", userId, e);
            }
        }

        private Order convertToOrder(BybitOrderData data) {
            OrderSide side = "Buy".equalsIgnoreCase(data.getSide()) ? OrderSide.BUY : OrderSide.SELL;
            OrderType orderType = "Market".equalsIgnoreCase(data.getOrderType()) ? OrderType.MARKET : OrderType.LIMIT;
            OrderStatus status = "Filled".equals(data.getOrderStatus()) ? OrderStatus.FILLED : OrderStatus.CANCELED;
            PositionEffect positionEffect = Boolean.TRUE.equals(data.getReduceOnly())
                    ? PositionEffect.CLOSE
                    : PositionEffect.OPEN;

            return Order.builder()
                    .user(exchangeApiKey.getUser())
                    .exchangeApiKey(exchangeApiKey)
                    .exchangeName(exchangeApiKey.getExchangeName())
                    .exchangeOrderId(data.getOrderId())
                    .symbol(data.getSymbol())
                    .side(side)
                    .orderType(orderType)
                    .positionEffect(positionEffect)
                    .filledQuantity(parseBigDecimal(data.getCumExecQty()))
                    .filledPrice(parseBigDecimal(data.getAvgPrice()))
                    .cumExecFee(parseBigDecimal(data.getCumExecFee()))
                    .realizedPnl(parseBigDecimal(data.getClosedPnl()))
                    .status(status)
                    .orderTime(parseTimestamp(data.getCreatedTime()))
                    .fillTime(parseTimestamp(data.getUpdatedTime()))
                    .positionIdx(data.getPositionIdx())
                    .orderLinkId(data.getOrderLinkId())
                    .build();
        }

        private Position convertToPosition(BybitPositionData data) {
            LocalDateTime updateTime = parseTimestamp(data.getUpdatedTime());
            return Position.builder()
                    .exchangeApiKey(exchangeApiKey)
                    .symbol(data.getSymbol())
                    .side(convertSide(data.getSide(), data.getPositionIdx()))
                    .avgEntryPrice(parseBigDecimal(data.getEntryPrice()))
                    .currentSize(parseBigDecimal(data.getSize()))
                    .leverage(parseInteger(data.getLeverage()))
                    .targetPrice(parseBigDecimal(data.getTakeProfit()))
                    .stopLossPrice(parseBigDecimal(data.getStopLoss()))
                    .realizedPnl(parseBigDecimal(data.getCurRealisedPnl()))
                    .entryTime(updateTime)
                    .status(convertStatus(data.getSize()))
                    .exchangeUpdateTime(updateTime)
                    .build();
        }

        private PositionSide convertSide(String side, Integer positionIdx) {
            if (side != null && !side.isEmpty()) {
                return "Buy".equalsIgnoreCase(side) ? PositionSide.LONG : PositionSide.SHORT;
            }
            // side가 비어있는 경우(포지션 종료 시) positionIdx로 추론
            if (positionIdx != null) {
                if (positionIdx == 1) return PositionSide.LONG;
                if (positionIdx == 2) return PositionSide.SHORT;
            }
            // positionIdx=0: 단방향 모드, side 불명 → null 반환 (symbol로만 조회)
            return null;
        }

        private PositionStatus convertStatus(String size) {
            if (size == null || size.isEmpty() || "0".equals(size)) {
                return PositionStatus.CLOSED;
            }
            return PositionStatus.OPEN;
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
            // 재연결 시 Gap 보완을 위해 끊긴 시간 기록 (처음 끊길 때만)
            disconnectTime.compareAndSet(null, LocalDateTime.now());
            log.warn("[Bybit] WebSocket closed - user: {}, code: {}, reason: {}, remote: {}",
                    userId, code, reason, remote);
            scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
            log.error("[Bybit] WebSocket error - user: {}", userId, ex);
            isConnected = false;
        }

        private void sendAuthMessage() {
            try {
                String expires = String.valueOf(System.currentTimeMillis() + 10000);
                String sign = BybitSignatureUtil.generateSignature(apiSecret, expires);
                String authMsg = String.format(
                        "{\"op\":\"auth\",\"args\":[\"%s\",%s,\"%s\"]}",
                        apiKey, expires, sign
                );
                send(authMsg);
                log.info("[Bybit] Auth message sent - user: {}", userId);
            } catch (Exception e) {
                log.error("[Bybit] Error sending auth message", e);
            }
        }
    }
}
