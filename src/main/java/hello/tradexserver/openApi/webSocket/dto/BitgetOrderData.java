package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetOrderData {
    private String orderId;
    private String clientOid;
    private String instId;           // Symbol e.g. "ETHUSDT"
    private String side;             // "buy" / "sell"
    private String tradeSide;        // "open" / "close" / "reduce_close_long" etc.
    private String posSide;          // "long" / "short" / "net"
    private String posMode;          // "hedge_mode" / "one_way_mode"
    private String orderType;        // "limit" / "market"
    private String price;            // 주문 가격
    private String size;             // 주문 수량
    private String accBaseVolume;    // 누적 체결 수량
    private String priceAvg;         // 평균 체결 가격
    private String leverage;
    private String marginMode;       // "crossed" / "isolated"
    private String marginCoin;
    private String status;           // "live" / "partially_filled" / "filled" / "canceled"
    private String totalProfits;     // 실현 손익
    private String reduceOnly;       // "yes" / "no"
    private List<FeeDetail> feeDetail;  // 수수료 상세 (누적)
    private String fillPrice;        // 최근 체결 가격
    private String fillTime;         // 최근 체결 시간
    @JsonProperty("cTime")
    private String cTime;            // 주문 생성 시간 (epoch millis)
    @JsonProperty("uTime")
    private String uTime;            // 주문 업데이트 시간 (epoch millis)

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeeDetail {
        private String feeCoin;
        private String fee;
    }
}