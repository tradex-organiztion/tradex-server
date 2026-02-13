package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;

import java.math.BigDecimal;

public interface ExchangeRestClient {

    BigDecimal getAsset(ExchangeApiKey apiKey);

    /**
     * 지갑 잔고 상세 조회 (총 자산 + 코인별 잔고)
     */
    WalletBalanceResponse getWalletBalance(ExchangeApiKey apiKey);
}
