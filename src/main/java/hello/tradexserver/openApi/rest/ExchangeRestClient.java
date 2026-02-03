package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.Order;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;

import java.math.BigDecimal;
import java.util.List;

public interface ExchangeRestClient {

    List<PositionResponse> getPositions(int limit);

    List<Order> getOrders();

    BigDecimal getAsset();

    /**
     * 지갑 잔고 상세 조회 (총 자산 + 코인별 잔고)
     */
    WalletBalanceResponse getWalletBalance();
}
