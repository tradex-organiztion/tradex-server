package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetOrderHistoryItem {
    private String orderId;
    private String clientOid;
    private String symbol;
    private String side;             // "buy" / "sell"
    private String tradeSide;        // "open" / "close" etc.
    private String posSide;          // "long" / "short" / "net"
    private String posMode;          // "hedge_mode" / "one_way_mode"
    private String orderType;        // "limit" / "market"
    private String status;           // "filled" / "canceled" etc.
    private String size;             // 주문 수량
    private String priceAvg;         // 평균 체결 가격
    private String baseVolume;       // 누적 체결 수량
    private String leverage;
    private String totalProfits;     // 실현 손익
    private String reduceOnly;       // "yes" / "no"
    private List<FeeDetail> feeDetail;
    @JsonProperty("cTime")
    private String cTime;            // epoch millis
    @JsonProperty("uTime")
    private String uTime;            // epoch millis

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeeDetail {
        private String feeCoin;
        private String fee;
    }
}