package hello.tradexserver.openApi.webSocket;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class WebSocketInitializer {

    @Autowired
    private ExchangeApiKeyRepository exchangeApiKeyRepository;

    @Autowired
    private ExchangeWebSocketManager exchangeWebSocketManager;

    /**
     * 애플리케이션 시작 시 모든 사용자의 거래소 WebSocket 연결
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeWebSockets() {
        log.info("Initializing WebSocket connections...");

        try {
            // DB에서 모든 활성화된 API Key 조회
            List<ExchangeApiKey> activeApiKeys = exchangeApiKeyRepository.findAllActive();

            log.info("Found {} active API keys to initialize", activeApiKeys.size());

            // 각 사용자별로 WebSocket 연결
            activeApiKeys.forEach(apiKey -> {
                try {
                    exchangeWebSocketManager.connectUser(
                            apiKey.getUser().getId(),
                            apiKey
                    );
                    log.info("WebSocket initialized - UserId: {}, Exchange: {}",
                            apiKey.getUser().getId(), apiKey.getExchangeName());
                } catch (Exception e) {
                    log.error("Failed to initialize WebSocket for user: {}, exchange: {}",
                            apiKey.getUser().getId(), apiKey.getExchangeName(), e);
                }
            });

            log.info("WebSocket initialization completed");
        } catch (Exception e) {
            log.error("Error during WebSocket initialization", e);
        }
    }
}
