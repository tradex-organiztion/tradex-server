package hello.tradexserver.openApi.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.openApi.rest.BinanceRestClient;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.web.client.RestTemplate;

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
public class BinanceWebSocketClient implements ExchangeWebSocketClient {

    // Testnet WebSocket
    private static final String WSS_BASE_URL = "wss://fstream.binancefuture.com";
    // Live WebSocket
    // private static final String WSS_BASE_URL = "wss://fstream.binance.com";

    private final Long userId;
    private final String apiKey;
    private final String apiSecret;
    private final RestTemplate restTemplate;
    private final BinanceRestClient binanceRestClient;

    private WebSocketClient wsClient;
    private final ObjectMapper objectMapper;
    private boolean isConnected = false;
    private PositionListener positionListener;
    private String listenKey;

    private ScheduledExecutorService reconnectExecutor;
    private ScheduledExecutorService keepAliveExecutor;
    private int reconnectAttempts = 0;
    private boolean shouldReconnect = true;

    public BinanceWebSocketClient(Long userId, String apiKey, String apiSecret, RestTemplate restTemplate) {
        this.userId = userId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.restTemplate = restTemplate;
        this.binanceRestClient = new BinanceRestClient(apiKey, apiSecret, restTemplate);
        this.objectMapper = new ObjectMapper();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        this.keepAliveExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void setPositionListener(PositionListener listener) {
        this.positionListener = listener;
    }

    @Override
    public void connect() {
        try {
            this.listenKey = binanceRestClient.createListenKey();
            if (listenKey == null || listenKey.isEmpty()) {
                log.error("[Binance] ListenKey 생성 실패, WebSocket 연결 불가 - user: {}", userId);
                return;
            }

            String wssUrl = WSS_BASE_URL + "/ws/" + listenKey;
            wsClient = new BinanceWebSocketImpl(new URI(wssUrl));
            wsClient.connect();
            log.info("[Binance] WebSocket 연결 시도 - user: {}", userId);

            startKeepAliveScheduler();
        } catch (URISyntaxException e) {
            log.error("[Binance] Invalid WebSocket URI", e);
        } catch (Exception e) {
            log.error("[Binance] WebSocket 연결 중 오류 - user: {}", userId, e);
        }
    }

    private void startKeepAliveScheduler() {
        keepAliveExecutor.scheduleAtFixedRate(() -> {
            try {
                binanceRestClient.keepAliveListenKey();
            } catch (Exception e) {
                log.error("[Binance] ListenKey 연장 실패 - user: {}", userId, e);
            }
        }, 30, 30, TimeUnit.MINUTES);
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;
        if (wsClient != null) {
            wsClient.close();
            isConnected = false;
            log.info("[Binance] WebSocket 연결 해제 - user: {}", userId);
        }
        shutdownExecutors();
    }

    private void shutdownExecutors() {
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdownNow();
        }
        if (keepAliveExecutor != null && !keepAliveExecutor.isShutdown()) {
            keepAliveExecutor.shutdownNow();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        if (reconnectAttempts >= ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS) {
            log.error("[Binance] 최대 재연결 시도 횟수({}) 도달 - user: {}",
                    ExchangeWebSocketClient.MAX_RECONNECT_ATTEMPTS, userId);
            return;
        }

        long delay = Math.min(
                ExchangeWebSocketClient.INITIAL_RECONNECT_DELAY_MS * (1L << reconnectAttempts),
                ExchangeWebSocketClient.MAX_RECONNECT_DELAY_MS
        );
        reconnectAttempts++;

        reconnectExecutor.schedule(() -> {
            if (shouldReconnect && !isConnected()) {
                log.info("[Binance] 재연결 시도 중 - user: {} (시도 {}/{})",
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
        // Binance User Data Stream은 별도의 구독 필요 없음
        log.info("[Binance] User Data Stream 연결됨 - 포지션 업데이트 자동 수신");
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
                            handleAccountUpdate(jsonNode);
                            break;
                        case "ORDER_TRADE_UPDATE":
                            handleOrderTradeUpdate(jsonNode);
                            break;
                        case "listenKeyExpired":
                            handleListenKeyExpired();
                            break;
                        default:
                            log.debug("[Binance] 이벤트 타입: {}", eventType);
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

            PositionSide side;
            if ("BOTH".equals(positionSideStr)) {
                // one-way 모드: amount 부호로 방향 결정
                side = positionAmt.compareTo(BigDecimal.ZERO) >= 0
                        ? PositionSide.LONG : PositionSide.SHORT;
            } else {
                side = "LONG".equals(positionSideStr) ? PositionSide.LONG : PositionSide.SHORT;
            }

            return Position.builder()
                    .symbol(posNode.path("s").asText())
                    .side(side)
                    .avgEntryPrice(parseBigDecimal(posNode.path("ep").asText("0")))
                    .currentSize(positionAmt.abs())
                    .totalSize(positionAmt.abs())
                    .entryTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(transactionTime), ZoneId.systemDefault()))
                    .status(positionAmt.compareTo(BigDecimal.ZERO) == 0
                            ? PositionStatus.CLOSED : PositionStatus.OPEN)
                    .build();
        }

        private void handleOrderTradeUpdate(JsonNode jsonNode) {
            log.info("[Binance] ORDER_TRADE_UPDATE - user: {}", userId);
            log.debug("[Binance] ORDER_TRADE_UPDATE raw: {}", jsonNode.toString());
        }

        private void handleListenKeyExpired() {
            log.warn("[Binance] ListenKey 만료됨 - 재연결 시도");
            isConnected = false;
            scheduleReconnect();
        }

        private BigDecimal parseBigDecimal(String value) {
            if (value == null || value.isEmpty()) return BigDecimal.ZERO;
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isConnected = false;
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
