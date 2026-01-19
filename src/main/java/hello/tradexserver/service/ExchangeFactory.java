package hello.tradexserver.service;

import hello.tradexserver.domain.enums.ExchangeName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class ExchangeFactory {

    private final RestTemplate restTemplate;

    public ExchangeService getExchangeService(ExchangeName exchangeName,
                                              String apiKey,
                                              String apiSecret) {
        return switch(exchangeName) {
            case BYBIT -> new ByBitExchangeService(apiKey, apiSecret, restTemplate);
        };
    }
}
