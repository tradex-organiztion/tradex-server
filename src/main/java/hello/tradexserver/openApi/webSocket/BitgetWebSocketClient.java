package hello.tradexserver.openApi.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.*;
import hello.tradexserver.openApi.util.BitgetSignatureUtil;
import hello.tradexserver.openApi.webSocket.dto.BitgetOrderData;
import hello.tradexserver.openApi.webSocket.dto.BitgetOrderMessage;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class BitgetWebSocketClient implements ExchangeWebSocketClient {

    // live용 base url
//    private static final String WSS_URL = "wss://ws.bitget.com/v2/ws/private";
    // demo용 base url
    private static final String WSS_URL = "wss://wspap.bitget.com/v2/ws/private";

    private final Long userId;
    private final ExchangeApiKey exchangeApiKey;
    private final ObjectMapper objectMapper;

    private WebSocketClient wsClient;
    private boolean isConnected = false;
    private boolean isAuthenticated = false;
    private PositionListener positionListener;
    private OrderListener orderListener;

    // 끊긴 시간 추적 - 재연결 시 Gap 보완에 사용
    private final AtomicReference<LocalDateTime> disconnectTime = new AtomicReference<>(null);

    // snapshot 비교용: 현재 추적 중인 오픈 포지션 (key: "instId_holdSide", value: PositionSide)
    private final ConcurrentHashMap<String, PositionSide> trackedPositions = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduledExecutor;
    private int reconnectAttempts = 0;
    private boolean shouldReconnect = true;

    public BitgetWebSocketClient(Long userId, ExchangeApiKey exchangeApiKey) {
        this.userId = userId;
        this.exchangeApiKey = exchangeApiKey;
        this.objectMapper = new ObjectMapper();
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
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
            // 포지션 + 오더 채널 동시 구독
            String subscribeMsg = "{\"op\":\"subscribe\",\"args\":[" +
                    "{\"instType\":\"USDT-FUTURES\",\"channel\":\"positions\",\"instId\":\"default\"}," +
                    "{\"instType\":\"USDT-FUTURES\",\"channel\":\"orders\",\"instId\":\"default\"}" +
                    "]}";
            wsClient.send(subscribeMsg);
            log.info("[Bitget] Position + Order subscription sent for user: {}", userId);
        } catch (Exception e) {
            log.error("[Bitget] Error subscribing - user: {}", userId, e);
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

                // 데이터 메시지 (포지션 / 오더)
                if (jsonNode.has("arg")) {
                    String channel = jsonNode.path("arg").path("channel").asText();
                    if ("positions".equals(channel)) {
                        System.out.println("[Bitget] Positions received "+message);
                        handlePositionMessage(message);
                        return;
                    }
                    if ("orders".equals(channel)) {
                        System.out.println("[Bitget] Orders received "+message);
                        handleOrderMessage(message);
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

                // 재연결 시 Gap 보완
                LocalDateTime gapStart = disconnectTime.getAndSet(null);
                if (gapStart != null) {
                    log.info("[Bitget] 재연결 감지 - Gap 보완 시작 (gapStart: {}) - user: {}", gapStart, userId);
                    if (orderListener != null) {
                        orderListener.onReconnected(exchangeApiKey, gapStart);
                    }
                    if (positionListener != null) {
                        positionListener.onReconnected(exchangeApiKey);
                    }
                }
            } else {
                log.error("[Bitget] Login failed - user: {}, code: {}, msg: {}",
                        userId, code, jsonNode.path("msg").asText());
            }
        }

        private void handleSubscribeResponse(JsonNode jsonNode) {
            log.info("[Bitget] Subscription successful - user: {}, channel: {}",
                    userId, jsonNode.path("arg").path("channel").asText());
        }

        // ================ 포지션 처리 ================
        private void handlePositionMessage(String message) {
            try {
                BitgetPositionMessage posMsg = objectMapper.readValue(message, BitgetPositionMessage.class);
                String action = posMsg.getAction();
                List<BitgetPositionData> dataList = posMsg.getData() != null ? posMsg.getData() : List.of();

                log.info("[Bitget] Position {} - user: {}, count: {}",
                        action, userId, dataList.size());

                if ("snapshot".equals(action)) {
                    handlePositionSnapshot(dataList);
                } else {
                    // "update" - 개별 포지션 변경 처리
                    for (BitgetPositionData data : dataList) {
                        processPositionData(data);
                    }
                }
            } catch (Exception e) {
                log.error("[Bitget] Error parsing position message - user: {}", userId, e);
            }
        }

        /**
         * snapshot: 현재 오픈 포지션 전체 목록과 tracked 비교
         * - snapshot에 있으면 → update
         * - tracked에만 있고 snapshot에 없으면 → close
         */
        private void handlePositionSnapshot(List<BitgetPositionData> snapshotData) {
            Set<String> snapshotKeys = new HashSet<>();

            // snapshot에 있는 포지션 처리 (update)
            for (BitgetPositionData data : snapshotData) {
                BigDecimal total = parseBigDecimal(data.getTotal());
                if (total.compareTo(BigDecimal.ZERO) != 0) {
                    String key = positionKey(data.getInstId(), data.getHoldSide());
                    snapshotKeys.add(key);
                    processPositionData(data);
                }
            }

            // tracked에만 있고 snapshot에 없는 포지션 → close 처리
            Iterator<Map.Entry<String, PositionSide>> it = trackedPositions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, PositionSide> entry = it.next();
                if (!snapshotKeys.contains(entry.getKey())) {
                    String[] parts = entry.getKey().split("_", 2);
                    String symbol = parts[0];
                    PositionSide side = entry.getValue();

                    log.info("[Bitget] Snapshot 기반 포지션 종료 감지 - symbol: {}, side: {}, user: {}",
                            symbol, side, userId);

                    if (positionListener != null) {
                        Position closedPosition = Position.builder()
                                .exchangeApiKey(exchangeApiKey)
                                .symbol(symbol)
                                .side(side)
                                .currentSize(BigDecimal.ZERO)
                                .avgEntryPrice(BigDecimal.ZERO)
                                .status(PositionStatus.CLOSED)
                                .exchangeUpdateTime(LocalDateTime.now())
                                .build();
                        positionListener.onPositionClosed(closedPosition);
                    }
                    it.remove();
                }
            }
        }

        private void processPositionData(BitgetPositionData data) {
            Position position = convertToPosition(data);
            BigDecimal total = parseBigDecimal(data.getTotal());
            String key = positionKey(data.getInstId(), data.getHoldSide());

            if (positionListener != null) {
                if (total.compareTo(BigDecimal.ZERO) == 0) {
                    trackedPositions.remove(key);
                    positionListener.onPositionClosed(position);
                } else {
                    trackedPositions.put(key, position.getSide());
                    positionListener.onPositionUpdate(position);
                }
            }
        }

        private String positionKey(String instId, String holdSide) {
            return instId + "_" + (holdSide != null ? holdSide.toLowerCase() : "net");
        }

        private Position convertToPosition(BitgetPositionData data) {
            BigDecimal totalSize = parseBigDecimal(data.getTotal());
            PositionSide side = convertHoldSide(data.getHoldSide(), totalSize);

            LocalDateTime updateTime = parseTimestamp(data.getUTime());

            return Position.builder()
                    .exchangeApiKey(exchangeApiKey)
                    .symbol(data.getInstId())
                    .side(side)
                    .avgEntryPrice(parseBigDecimal(data.getOpenPriceAvg()))
                    .currentSize(totalSize.abs())
                    .leverage(parseInteger(data.getLeverage()))
                    .realizedPnl(parseBigDecimal(data.getAchievedProfits()))
                    .entryTime(updateTime)
                    .status(totalSize.compareTo(BigDecimal.ZERO) == 0
                            ? PositionStatus.CLOSED : PositionStatus.OPEN)
                    .exchangeUpdateTime(updateTime)
                    .build();
        }

        private PositionSide convertHoldSide(String holdSide, BigDecimal totalSize) {
            if ("long".equalsIgnoreCase(holdSide)) {
                return PositionSide.LONG;
            } else if ("short".equalsIgnoreCase(holdSide)) {
                return PositionSide.SHORT;
            } else {
                // "net" (원웨이 모드): 닫힌 포지션(total=0)이면 side 불명 → null
                if (totalSize.compareTo(BigDecimal.ZERO) == 0) {
                    return null;
                }
                return totalSize.compareTo(BigDecimal.ZERO) > 0
                        ? PositionSide.LONG : PositionSide.SHORT;
            }
        }

        // ================ 오더 처리 ================
        private void handleOrderMessage(String message) {
            try {
                BitgetOrderMessage orderMsg = objectMapper.readValue(message, BitgetOrderMessage.class);
                if (orderMsg.getData() == null || orderMsg.getData().isEmpty()) return;

                log.info("[Bitget] Order {} - user: {}, count: {}",
                        orderMsg.getAction(), userId, orderMsg.getData().size());

                for (BitgetOrderData data : orderMsg.getData()) {
                    String status = data.getStatus();
                    if (!shouldSaveOrder(status, data)) continue;

                    Order order = convertToOrder(data);
                    if (orderListener != null) {
                        orderListener.onOrderReceived(order);
                    }
                }
            } catch (Exception e) {
                log.error("[Bitget] Error parsing order message - user: {}", userId, e);
            }
        }

        private boolean shouldSaveOrder(String status, BitgetOrderData data) {
            if ("filled".equals(status)) return true;
            if ("canceled".equals(status)) {
                BigDecimal filledQty = parseBigDecimal(data.getAccBaseVolume());
                return filledQty.compareTo(BigDecimal.ZERO) > 0;
            }
            return false;
        }

        private Order convertToOrder(BitgetOrderData data) {
            OrderSide side = "buy".equalsIgnoreCase(data.getSide()) ? OrderSide.BUY : OrderSide.SELL;
            OrderType orderType = "market".equalsIgnoreCase(data.getOrderType()) ? OrderType.MARKET : OrderType.LIMIT;
            OrderStatus status = "filled".equals(data.getStatus()) ? OrderStatus.FILLED : OrderStatus.CANCELED;
            PositionEffect positionEffect = convertTradeSideToPositionEffect(data.getTradeSide());
            Integer positionIdx = convertPosSideToIdx(data.getPosSide());

            BigDecimal cumFee = calculateTotalFee(data);

            LocalDateTime orderTime = parseTimestamp(data.getCTime());
            LocalDateTime fillTime = "filled".equals(data.getStatus())
                    ? parseTimestamp(data.getUTime()) : null;

            return Order.builder()
                    .user(exchangeApiKey.getUser())
                    .exchangeApiKey(exchangeApiKey)
                    .exchangeName(exchangeApiKey.getExchangeName())
                    .exchangeOrderId(data.getOrderId())
                    .symbol(data.getInstId())
                    .side(side)
                    .orderType(orderType)
                    .positionEffect(positionEffect)
                    .filledQuantity(parseBigDecimal(data.getAccBaseVolume()))
                    .filledPrice(parseBigDecimal(data.getPriceAvg()))
                    .cumExecFee(cumFee)
                    .realizedPnl(parseBigDecimal(data.getTotalProfits()))
                    .status(status)
                    .orderTime(orderTime)
                    .fillTime(fillTime)
                    .positionIdx(positionIdx)
                    .build();
        }

        private PositionEffect convertTradeSideToPositionEffect(String tradeSide) {
            if (tradeSide == null) return PositionEffect.OPEN;
            if ("open".equals(tradeSide) || "buy_single".equals(tradeSide) || "sell_single".equals(tradeSide)) {
                return PositionEffect.OPEN;
            }
            return PositionEffect.CLOSE;
        }

        private Integer convertPosSideToIdx(String posSide) {
            if (posSide == null) return 0;
            return switch (posSide) {
                case "long" -> 1;
                case "short" -> 2;
                default -> 0; // "net"
            };
        }

        private BigDecimal calculateTotalFee(BitgetOrderData data) {
            if (data.getFeeDetail() == null || data.getFeeDetail().isEmpty()) return BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;
            for (BitgetOrderData.FeeDetail fd : data.getFeeDetail()) {
                BigDecimal fee = parseBigDecimal(fd.getFee());
                total = total.add(fee);
            }
            return total;
        }

        // ================ 유틸 ================
        private void sendLoginMessage() {
            try {
                // Bitget WebSocket은 timestamp를 초 단위로 사용
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                String sign = BitgetSignatureUtil.generateWebSocketSignature(
                        exchangeApiKey.getApiSecret(), timestamp);

                String loginMsg = String.format(
                        "{\"op\":\"login\",\"args\":[{\"apiKey\":\"%s\",\"passphrase\":\"%s\"," +
                                "\"timestamp\":\"%s\",\"sign\":\"%s\"}]}",
                        exchangeApiKey.getApiKey(), exchangeApiKey.getPassphrase(), timestamp, sign
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
            disconnectTime.compareAndSet(null, LocalDateTime.now());
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