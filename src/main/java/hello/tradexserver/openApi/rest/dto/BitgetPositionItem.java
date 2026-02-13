package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetPositionItem {
    private String posId;
    private String instId;            // Symbol e.g. "BTCUSDT"
    private String holdSide;          // "long" / "short" / "net"
    private String posMode;           // "hedge_mode" / "one_way_mode"
    private String total;             // 총 포지션 수량
    private String available;
    private String openPriceAvg;      // 평균 진입가
    private String leverage;
    private String achievedProfits;   // 실현 손익
    private String unrealizedPL;
    private String marginCoin;
    private String marginMode;
    @JsonProperty("cTime")
    private String cTime;
    @JsonProperty("uTime")
    private String uTime;
}