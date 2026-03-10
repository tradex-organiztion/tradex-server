package hello.tradexserver.openApi.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.*;
import hello.tradexserver.openApi.rest.BinanceRestClient;
import hello.tradexserver.openApi.rest.dto.BinancePositionRisk;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class BinanceWebSocketClient implements ExchangeWebSocketClient {

    // Testnet WebSocket
    // private static final String WSS_BASE_URL = "wss://fstream.binancefuture.com";
    // Live WebSocket
    private static final String WSS_BASE_URL = "wss://fstream.binance.com";

    private final Long userId;
    private final ExchangeApiKey exchangeApiKey;
    private final BinanceRestClient binanceRestClient;
    private final ObjectMapper objectMapper;

    private WebSocketClient wsClient;
    private boolean isConnected = false;
    private PositionListener positionListener;
    private OrderListener orderListener;
    private String listenKey;

    // 끊긴 시간 추적 - 재연결 시 Gap 보완에 사용
    private final AtomicReference<LocalDateTime> disconnectTime = new AtomicReference<>(null);

    // Binance ORDER_TRADE_UPDATE의 n(수수료)은 이번 체결분만 → orderId별 누적 필요
    private final ConcurrentHashMap<Long, BigDecimal> orderFeeAccumulator = new ConcurrentHashMap<>();

    private final WebSocketScheduler scheduler;
    private ScheduledFuture<?> keepAliveFuture;
    private int reconnectAttempts = 0;
    private boolean shouldReconnect = true;

    public BinanceWebSocketClient(Long userId, ExchangeApiKey exchangeApiKey,
                                   BinanceRestClient binanceRestClient, WebSocketScheduler scheduler) {
        this.userId = userId;
        this.exchangeApiKey = exchangeApiKey;
        this.binanceRestClient = binanceRestClient;
        this.objectMapper = new ObjectMapper();
        this.scheduler = scheduler;
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
            this.listenKey = binanceRestClient.createListenKey(exchangeApiKey);
            if (listenKey == null || listenKey.isEmpty()) {
                log.error("[Binance] ListenKey 생성 실패, WebSocket 연결 불가 - user: {}", userId);
                return;
            }

            String wssUrl = WSS_BASE_URL + "/ws/" + listenKey;
            wsClient = new BinanceWebSocketImpl(new URI(wssUrl));
            wsClient.connect();
            log.info("[Binance] WebSocket 연결 시도 - user: {}", userId);

            startKeepAliveScheduler();
        } catch (Exception e) {
            log.error("[Binance] WebSocket 연결 중 오류 - user: {}", userId, e);
        }
    }

    private void startKeepAliveScheduler() {
        stopKeepAliveScheduler();
        keepAliveFuture = scheduler.scheduleHeavyTask(() -> {
            try {
                binanceRestClient.keepAliveListenKey(exchangeApiKey);
            } catch (Exception e) {
                log.error("[Binance] ListenKey 연장 실패 - user: {}", userId, e);
            }
        }, 30, 30);
    }

    private void stopKeepAliveScheduler() {
        if (keepAliveFuture != null && !keepAliveFuture.isDone()) {
            keepAliveFuture.cancel(false);
            keepAliveFuture = null;
        }
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;
        stopKeepAliveScheduler();
        if (wsClient != null) {
            wsClient.close();
            isConnected = false;
            log.info("[Binance] WebSocket 연결 해제 - user: {}", userId);
        }
        orderFeeAccumulator.clear();
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.error("[Binance] 최대 재연결 시도 횟수({}) 도달 - user: {}", MAX_RECONNECT_ATTEMPTS, userId);
            return;
        }

        long delay = Math.min(
                INITIAL_RECONNECT_DELAY_MS * (1L << reconnectAttempts),
                MAX_RECONNECT_DELAY_MS
        );
        reconnectAttempts++;

        log.info("[Binance] 재연결 시도 {}/{} 예약 - user: {}, {}ms 후",
                reconnectAttempts, MAX_RECONNECT_ATTEMPTS, userId, delay);

        scheduler.scheduleReconnect(() -> {
            if (shouldReconnect && !isConnected()) {
                connect();
            }
        }, delay);
    }

    @Override
    public boolean isConnected() {
        return isConnected && wsClient != null && wsClient.isOpen();
    }

    @Override
    public void subscribePosition() {
        // Binance User Data Stream은 별도의 구독 필요 없음 - 연결 즉시 자동 수신
        log.info("[Binance] User Data Stream 연결됨 - 포지션/오더 업데이트 자동 수신 - user: {}", userId);
    }

    // ================ 내부 WebSocket 클래스 ================
    private class BinanceWebSocketImpl extends WebSocketClient {
        public BinanceWebSocketImpl(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            isConnected = true;
            reconnectAttempts = 0;
            log.info("[Binance] WebSocket 연결 성공 - user: {}", userId);
            subscribePosition();

            // 연결/재연결 시 Gap 보완 (첫 연결 시 gapStart=null)
            LocalDateTime gapStart = disconnectTime.getAndSet(null);
            if (gapStart != null) {
                log.info("[Binance] 재연결 감지 - Gap 보완 시작 (gapStart: {}) - user: {}", gapStart, userId);
            } else {
                log.info("[Binance] 첫 연결 - 초기 보완 시작 - user: {}", userId);
            }
            if (orderListener != null) {
                orderListener.onReconnected(exchangeApiKey, gapStart);
            }
            if (positionListener != null) {
                positionListener.onReconnected(exchangeApiKey);
            }
        }

        @Override
        public void onMessage(String message) {
            log.debug("[Binance] Raw Message 수신: {}", message);

            try {
                JsonNode jsonNode = objectMapper.readTree(message);

                if (jsonNode.has("e")) {
                    String eventType = jsonNode.get("e").asText();

                    switch (eventType) {
                        case "ACCOUNT_UPDATE":
                            System.out.println(jsonNode);
                            handleAccountUpdate(jsonNode);
                            break;
                        case "ORDER_TRADE_UPDATE":
                            handleOrderTradeUpdate(jsonNode);
                            break;
                        case "listenKeyExpired":
                            handleListenKeyExpired();
                            break;
                        default:
                            log.debug("[Binance] 이벤트 타입: {} - user: {}", eventType, userId);
                    }
                }
            } catch (Exception e) {
                log.error("[Binance] 메시지 처리 중 오류 - user: {}", userId, e);
            }
        }

        private void handleAccountUpdate(JsonNode jsonNode) {
            JsonNode updateData = jsonNode.path("a");
            String eventReason = updateData.path("m").asText();
            long transactionTime = jsonNode.path("T").asLong();

            log.info("[Binance] ACCOUNT_UPDATE - reason: {}, user: {}", eventReason, userId);

            // ORDER 또는 LIQUIDATION 이벤트만 포지션 처리
            if (!"ORDER".equals(eventReason) && !"LIQUIDATION".equals(eventReason)) {
                log.debug("[Binance] 비거래 ACCOUNT_UPDATE 스킵: {}", eventReason);
                return;
            }

            JsonNode positions = updateData.path("P");
            if (positions.isArray() && positionListener != null) {
                for (JsonNode posNode : positions) {
                    Position position = convertToPosition(posNode, transactionTime);

                    BigDecimal positionAmt = parseBigDecimal(posNode.path("pa").asText("0"));
                    if (positionAmt.compareTo(BigDecimal.ZERO) == 0) {
                        positionListener.onPositionClosed(position);
                    } else {
                        positionListener.onPositionUpdate(position);
                    }
                }
            }
        }

        private Position convertToPosition(JsonNode posNode, long transactionTime) {
            String positionSideStr = posNode.path("ps").asText(); // BOTH, LONG, SHORT
            BigDecimal positionAmt = parseBigDecimal(posNode.path("pa").asText("0"));
            String symbol = posNode.path("s").asText();

            PositionSide side;
            if ("LONG".equals(positionSideStr)) {
                side = PositionSide.LONG;
            } else if ("SHORT".equals(positionSideStr)) {
                side = PositionSide.SHORT;
            } else {
                // BOTH (원웨이 모드): 포지션 닫힘(pa=0)이면 side 불명 → null
                if (positionAmt.compareTo(BigDecimal.ZERO) == 0) {
                    side = null;
                } else {
                    side = positionAmt.compareTo(BigDecimal.ZERO) > 0
                            ? PositionSide.LONG : PositionSide.SHORT;
                }
            }

            // leverage는 ACCOUNT_UPDATE에 없음 → REST 보완
            Integer leverage = fetchLeverage(symbol);

            LocalDateTime eventTime = parseMillisToLocalDateTime(transactionTime);

            return Position.builder()
                    .exchangeApiKey(exchangeApiKey)
                    .symbol(symbol)
                    .side(side)
                    .avgEntryPrice(parseBigDecimal(posNode.path("ep").asText("0")))
                    .currentSize(positionAmt.abs())
                    .leverage(leverage)
                    .realizedPnl(parseBigDecimal(posNode.path("cr").asText("0")))
                    .entryTime(eventTime)
                    .status(positionAmt.compareTo(BigDecimal.ZERO) == 0
                            ? PositionStatus.CLOSED : PositionStatus.OPEN)
                    .exchangeUpdateTime(eventTime)
                    .build();
        }

        /**
         * REST로 leverage 조회 (캐시 없이 매번 호출 - 이벤트 빈도 낮음)
         */
        private Integer fetchLeverage(String symbol) {
            try {
                List<BinancePositionRisk> risks = binanceRestClient.fetchPositionRisk(exchangeApiKey, symbol);
                if (!risks.isEmpty()) {
                    return Integer.parseInt(risks.get(0).getLeverage());
                }
            } catch (Exception e) {
                log.warn("[Binance] leverage 조회 실패 - symbol: {}, user: {}", symbol, userId, e);
            }
            return null;
        }

        private void handleOrderTradeUpdate(JsonNode jsonNode) {
            JsonNode orderData = jsonNode.path("o");
            String executionType = orderData.path("x").asText(); // NEW, TRADE, CANCELED, EXPIRED
            String orderStatus = orderData.path("X").asText();   // NEW, PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED
            long orderId = orderData.path("i").asLong();

            log.info("[Binance] ORDER_TRADE_UPDATE - execType: {}, status: {}, orderId: {}, user: {}",
                    executionType, orderStatus, orderId, userId);

            // TRADE 이벤트: 수수료 누적 (n은 이번 체결분만)
            if ("TRADE".equals(executionType)) {
                BigDecimal commission = parseBigDecimal(orderData.path("n").asText("0"));
                orderFeeAccumulator.merge(orderId, commission, BigDecimal::add);
            }

            // 최종 상태에서만 Order 저장
            if (shouldSaveOrder(orderStatus, orderData)) {
                Order order = convertToOrder(orderData, orderId, orderStatus);
                if (orderListener != null) {
                    orderListener.onOrderReceived(order);
                }
                orderFeeAccumulator.remove(orderId);
            }
        }

        private boolean shouldSaveOrder(String orderStatus, JsonNode orderData) {
            if ("FILLED".equals(orderStatus)) return true;

            if ("CANCELED".equals(orderStatus) || "EXPIRED".equals(orderStatus)
                    || "EXPIRED_IN_MATCH".equals(orderStatus)) {
                BigDecimal filledQty = parseBigDecimal(orderData.path("z").asText("0"));
                return filledQty.compareTo(BigDecimal.ZERO) > 0;
            }
            return false;
        }

        private Order convertToOrder(JsonNode o, long orderId, String orderStatus) {
            String sideStr = o.path("S").asText();
            OrderSide side = "BUY".equalsIgnoreCase(sideStr) ? OrderSide.BUY : OrderSide.SELL;

            String typeStr = o.path("o").asText();
            OrderType orderType = "MARKET".equalsIgnoreCase(typeStr) ? OrderType.MARKET : OrderType.LIMIT;

            OrderStatus status = "FILLED".equals(orderStatus) ? OrderStatus.FILLED : OrderStatus.CANCELED;

            boolean reduceOnly = o.path("R").asBoolean(false);
            PositionEffect positionEffect = reduceOnly ? PositionEffect.CLOSE : PositionEffect.OPEN;

            String positionSide = o.path("ps").asText("BOTH");
            Integer positionIdx = convertPositionSideToIdx(positionSide);

            BigDecimal cumFee = orderFeeAccumulator.getOrDefault(orderId, BigDecimal.ZERO);

            long tradeTime = o.path("T").asLong();

            return Order.builder()
                    .user(exchangeApiKey.getUser())
                    .exchangeApiKey(exchangeApiKey)
                    .exchangeName(exchangeApiKey.getExchangeName())
                    .exchangeOrderId(String.valueOf(orderId))
                    .symbol(o.path("s").asText())
                    .side(side)
                    .orderType(orderType)
                    .positionEffect(positionEffect)
                    .filledQuantity(parseBigDecimal(o.path("z").asText("0")))
                    .filledPrice(parseBigDecimal(o.path("ap").asText("0")))
                    .cumExecFee(cumFee)
                    .realizedPnl(parseBigDecimal(o.path("rp").asText("0")))
                    .status(status)
                    .orderTime(parseMillisToLocalDateTime(tradeTime))
                    .fillTime(parseMillisToLocalDateTime(tradeTime))
                    .positionIdx(positionIdx)
                    .build();
        }

        private Integer convertPositionSideToIdx(String positionSide) {
            if (positionSide == null) return 0;
            return switch (positionSide) {
                case "LONG" -> 1;
                case "SHORT" -> 2;
                default -> 0; // BOTH
            };
        }

        private void handleListenKeyExpired() {
            log.warn("[Binance] ListenKey 만료됨 - 재연결 시도 - user: {}", userId);
            isConnected = false;
            disconnectTime.compareAndSet(null, LocalDateTime.now());
            scheduleReconnect();
        }

        private BigDecimal parseBigDecimal(String value) {
            if (value == null || value.isEmpty()) return BigDecimal.ZERO;
            try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
        }

        private LocalDateTime parseMillisToLocalDateTime(long millis) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isConnected = false;
            disconnectTime.compareAndSet(null, LocalDateTime.now());
            log.warn("[Binance] WebSocket 연결 종료 - user: {}, code: {}, reason: {}, remote: {}",
                    userId, code, reason, remote);
            scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
            log.error("[Binance] WebSocket 오류 - user: {}", userId, ex);
            isConnected = false;
        }
    }
}
