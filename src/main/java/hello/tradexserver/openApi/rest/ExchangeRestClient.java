package hello.tradexserver.openApi.rest;

import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.openApi.rest.dto.TradeRecord;

import java.math.BigDecimal;
import java.util.List;

public interface ExchangeRestClient {

    List<PositionResponse> getPositions(int limit);

    List<TradeRecord> getTradeHistory(String symbol, Long startTime, int limit);

    BigDecimal getAsset();
}
