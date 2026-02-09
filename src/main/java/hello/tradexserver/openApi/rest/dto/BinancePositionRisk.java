package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinancePositionRisk {
    private String symbol;
    private String positionAmt;
    private String entryPrice;
    private String markPrice;
    private String unRealizedProfit;
    private String liquidationPrice;
    private String leverage;
    private String marginType;      // isolated / cross
    private String positionSide;    // BOTH / LONG / SHORT
    private Long updateTime;
}
