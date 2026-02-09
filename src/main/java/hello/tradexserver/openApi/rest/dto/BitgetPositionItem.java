package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetPositionItem {
    private String symbol;
    private String holdSide;          // "long" / "short" / "" (one-way)
    private String holdMode;          // "one_way_mode" / "double_hold"
    private String total;             // 총 포지션 수량
    private String available;
    private String averageOpenPrice;
    private String leverage;
    private String unrealizedPL;
    private String achievedProfits;   // realized PnL
    private String marginCoin;
    private String marginMode;        // "crossed" / "isolated"
    private String cTime;             // create time (epoch millis)
    private String uTime;             // update time (epoch millis)
    private String liquidationPrice;
    private String breakEvenPrice;
    private String markPrice;
}