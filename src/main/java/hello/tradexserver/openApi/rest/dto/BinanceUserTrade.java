package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceUserTrade {
    private Long orderId;
    private String symbol;
    private String commission;       // 수수료
    private String commissionAsset;
    private String realizedPnl;
    private String side;             // BUY, SELL
    private String positionSide;     // BOTH, LONG, SHORT
    private Long time;               // 체결 시간 (millis)
}