package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinancePositionRisk {
    private String symbol;
    private String positionAmt;      // 포지션 수량 (부호 포함)
    private String entryPrice;
    private String breakEvenPrice;
    private String leverage;
    private String unrealizedProfit;
    private String positionSide;     // BOTH, LONG, SHORT
    private Long updateTime;
}