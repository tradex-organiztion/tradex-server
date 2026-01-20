package hello.tradexserver.config;

import hello.tradexserver.openApi.webSocket.ExchangeWebSocketManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebSocketShutdownHandler {

    @Autowired
    private ExchangeWebSocketManager exchangeWebSocketManager;

    /**
     * 애플리케이션 종료 시 모든 WebSocket 연결 종료
     */
    @EventListener(ContextClosedEvent.class)
    public void shutdownWebSockets() {
        log.info("Shutting down WebSocket connections...");
        exchangeWebSocketManager.shutdown();
        log.info("WebSocket shutdown completed");
    }
}
