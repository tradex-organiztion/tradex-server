package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetFillItem {
    private String tradeId;
    private String orderId;
    private String symbol;
    private String side;          // "buy" / "sell"
    private String tradeSide;     // "open" / "close" / "" (one-way)
    private String price;
    private String size;
    private String fee;
    private String feeCurrency;
    private String profit;        // realized profit for this fill
    private String cTime;         // epoch millis
}