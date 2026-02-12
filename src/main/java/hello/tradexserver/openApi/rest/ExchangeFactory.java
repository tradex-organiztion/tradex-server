package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.openApi.rest.bybit.BybitRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class ExchangeFactory {

    private final RestTemplate restTemplate;
    private final BybitRestClient bybitRestClient;

    public ExchangeRestClient getExchangeService(ExchangeName exchangeName,
                                                 String apiKey,
                                                 String apiSecret,
                                                 String passphrase) {
        return switch (exchangeName) {
            case BYBIT -> bybitRestClient;
            case BINANCE -> new BinanceRestClient(apiKey, apiSecret, restTemplate);
            case BITGET -> new BitgetRestClient(apiKey, apiSecret, passphrase, restTemplate);
        };
    }

    public ExchangeRestClient getExchangeService(ExchangeName exchangeName,
                                                 String apiKey,
                                                 String apiSecret) {
        return getExchangeService(exchangeName, apiKey, apiSecret, null);
    }

    public BinanceRestClient getBinanceRestClient(String apiKey, String apiSecret) {
        return new BinanceRestClient(apiKey, apiSecret, restTemplate);
    }
}