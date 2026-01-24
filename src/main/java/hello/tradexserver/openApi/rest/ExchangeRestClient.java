package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.Order;
import hello.tradexserver.dto.response.PositionResponse;

import java.math.BigDecimal;
import java.util.List;

public interface ExchangeRestClient {

    List<PositionResponse> getPositions(int limit);
    List<Order> getOrders();
    BigDecimal getAsset();
}
