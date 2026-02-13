package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.openApi.rest.bybit.BybitRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeFactory {

    private final BybitRestClient bybitRestClient;
    private final BinanceRestClient binanceRestClient;
    private final BitgetRestClient bitgetRestClient;

    public ExchangeRestClient getExchangeService(ExchangeName exchangeName,
                                                 String apiKey,
                                                 String apiSecret,
                                                 String passphrase) {
        return switch (exchangeName) {
            case BYBIT -> bybitRestClient;
            case BINANCE -> binanceRestClient;
            case BITGET -> bitgetRestClient;
        };
    }

    public ExchangeRestClient getExchangeService(ExchangeName exchangeName,
                                                 String apiKey,
                                                 String apiSecret) {
        return getExchangeService(exchangeName, apiKey, apiSecret, null);
    }
}