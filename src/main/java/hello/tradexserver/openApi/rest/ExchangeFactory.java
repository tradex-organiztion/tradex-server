package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.enums.ExchangeName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class ExchangeFactory {

    private final RestTemplate restTemplate;

    public ExchangeRestClient getExchangeService(ExchangeName exchangeName,
                                                 String apiKey,
                                                 String apiSecret) {
        return switch(exchangeName) {
            case BYBIT -> new ByBitRestClient(apiKey, apiSecret, restTemplate);
        };
    }
}
