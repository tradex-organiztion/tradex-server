package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetPositionData {
    private String instId;            // e.g. "BTCUSDT"
    private String holdSide;          // "long" / "short" / "net"
    private String holdMode;          // "one_way_mode" / "double_hold"
    private String total;             // 총 포지션 수량
    private String available;
    private String averageOpenPrice;
    private String leverage;
    private String achievedProfits;   // realized PnL
    private String unrealizedPL;
    private String marginCoin;
    private String marginMode;
    private String cTime;             // create time (epoch millis)
    private String uTime;             // update time (epoch millis)
    private String liquidationPrice;
    private String breakEvenPrice;
    private String markPrice;
}