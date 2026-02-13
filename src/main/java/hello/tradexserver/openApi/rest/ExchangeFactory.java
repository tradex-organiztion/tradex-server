package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.enums.ExchangeName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeFactory {

    private final BybitRestClient bybitRestClient;
    private final BinanceRestClient binanceRestClient;
    private final BitgetRestClient bitgetRestClient;

    public ExchangeRestClient getExchangeService(ExchangeName exchangeName) {
        return switch (exchangeName) {
            case BYBIT -> bybitRestClient;
            case BINANCE -> binanceRestClient;
            case BITGET -> bitgetRestClient;
        };
    }
}