package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Bitget orders-algo 채널 데이터 (TPSL 알고 주문)
 * <p>
 * planType 값:
 *   profit_plan  → TP (지정가 기반)
 *   loss_plan    → SL (지정가 기반)
 *   pos_profit   → 포지션 전체 TP
 *   pos_loss     → 포지션 전체 SL
 * <p>
 * status 값:
 *   live      → 대기 중 (SET)
 *   executed  → 체결됨 (TRIGGERED)
 *   cancelled → 취소됨 (CANCELED)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetAlgoOrderData {

    private String orderId;
    private String instId;        // 거래 페어 (예: "BTCUSDT")
    private String planType;      // profit_plan / loss_plan / pos_profit / pos_loss
    private String triggerPrice;  // 트리거 가격
    private String executePrice;  // 체결 가격 (지정가인 경우)
    private String status;        // live / executed / cancelled
    private String posSide;       // long / short / net

    @JsonProperty("cTime")
    private String cTime;         // 생성 시각 (epoch millis)

    @JsonProperty("uTime")
    private String uTime;         // 업데이트 시각 (epoch millis)
}
