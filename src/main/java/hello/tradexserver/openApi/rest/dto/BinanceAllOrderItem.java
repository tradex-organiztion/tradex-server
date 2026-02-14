package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceAllOrderItem {
    private Long orderId;
    private String symbol;
    private String status;           // NEW, PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED, EXPIRED_IN_MATCH
    private String side;             // BUY, SELL
    private String type;             // MARKET, LIMIT, STOP, etc.
    private String avgPrice;
    private String executedQty;      // 누적 체결 수량
    private Boolean reduceOnly;
    private String positionSide;     // BOTH, LONG, SHORT
    private Long time;               // 주문 시간 (millis)
    private Long updateTime;         // 마지막 업데이트 시간 (millis)
}