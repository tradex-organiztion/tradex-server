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

    /**
     * API 키 유효성 검증
     * 거래소에 가벼운 인증 API를 호출하여 키가 유효한지 확인
     */
    boolean validateApiKey(ExchangeApiKey apiKey);
}
