package hello.tradexserver.openApi.webSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.openApi.util.BybitSignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class BybitWebSocketClient implements ExchangeWebSocketClient {
    private static final String WSS_URL = "wss://stream-testnet.bybit.com/v5/private";

    private Long userId;
    private String apiKey;
    private String apiSecret;
    private WebSocketClient wsClient;
    private ObjectMapper objectMapper;
    private boolean isConnected = false;

    public BybitWebSocketClient(Long userId, String apiKey, String apiSecret) {
        this.userId = userId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.objectMapper = new ObjectMapper();
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
        if (wsClient != null) {
            wsClient.close();
            isConnected = false;
            log.info("Bybit WebSocket disconnected for user: {}", userId);
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected && wsClient != null && wsClient.isOpen();
    }

    // ================ 내부 WebSocket 클래스 ================
    private class BybitWebSocketImpl extends WebSocketClient {
        public BybitWebSocketImpl(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            isConnected = true;
            log.info("✅ Bybit WebSocket opened for user: {}", userId);
            sendAuthMessage();
        }

        @Override
        public void onMessage(String message) {
            log.debug("📨 Message received: {}", message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isConnected = false;
            log.warn("❌ Bybit WebSocket closed for user: {} - Code: {}, Reason: {}",
                    userId, code, reason);
        }

        @Override
        public void onError(Exception ex) {
            log.error("❌ Bybit WebSocket error for user: {}", userId, ex);
            isConnected = false;
        }

        private void sendAuthMessage() {
            try {
                String timestamp = String.valueOf(System.currentTimeMillis());
                String sign = BybitSignatureUtil.generateSignature(apiSecret, timestamp);

                String authMsg = String.format(
                        "{\"op\":\"auth\",\"args\":[\"%s\",\"%s\",\"%s\"]}",
                        apiKey, timestamp, sign
                );
                send(authMsg);
                log.info("🔐 Auth message sent for user: {}", userId);
            } catch (Exception e) {
                log.error("Error sending auth message", e);
            }
        }
    }
}
