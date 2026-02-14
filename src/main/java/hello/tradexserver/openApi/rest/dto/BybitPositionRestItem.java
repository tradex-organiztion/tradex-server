package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitPositionRestItem {
    private Integer positionIdx;
    private String symbol;
    private String side;       // Buy / Sell / ""(empty position)
    private String size;
    private String avgPrice;
    private String leverage;
    private String takeProfit;
    private String stopLoss;
    private String curRealisedPnl;
    private String createdTime;
    private String updatedTime;
}