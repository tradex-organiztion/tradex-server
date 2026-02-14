package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetPositionData {
    private String posId;             // 포지션 고유 ID
    private String instId;            // e.g. "BTCUSDT"
    private String holdSide;          // "long" / "short" / "net"
    private String posMode;           // "hedge_mode" / "one_way_mode"
    private String total;             // 총 포지션 수량
    private String available;
    private String openPriceAvg;      // 평균 진입가
    private String leverage;
    private String achievedProfits;   // realized PnL
    private String unrealizedPL;
    private String marginCoin;
    private String marginMode;        // "crossed" / "isolated"
    @JsonProperty("cTime")
    private String cTime;             // create time (epoch millis)
    @JsonProperty("uTime")
    private String uTime;             // update time (epoch millis)
    private String liquidationPrice;
    private String breakEvenPrice;
    private String markPrice;
    private String totalFee;          // 누적 수수료
    private String deductedFee;       // 차감된 거래 수수료
}