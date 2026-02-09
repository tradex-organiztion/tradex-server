package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceTrade {
    private Long id;
    private String orderId;
    private String symbol;
    private String side;            // BUY / SELL
    private String price;
    private String qty;
    private String commission;
    private String commissionAsset;
    private String realizedPnl;
    private Long time;              // epoch millis
    private Boolean buyer;
    private Boolean maker;
    private String positionSide;    // BOTH / LONG / SHORT
}
