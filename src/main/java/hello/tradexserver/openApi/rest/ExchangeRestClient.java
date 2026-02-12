package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.ExchangeApiKey;

import java.math.BigDecimal;

public interface ExchangeRestClient {

    BigDecimal getAsset(ExchangeApiKey apiKey);
}
