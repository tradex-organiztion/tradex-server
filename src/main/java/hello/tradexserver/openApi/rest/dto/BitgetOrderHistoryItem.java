package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetOrderHistoryItem {
    private String orderId;
    private String symbol;
    private String side;          // "buy" / "sell"
    private String tradeSide;     // "open" / "close"
    private String orderType;
    private String price;
    private String size;
    private String status;
    private String cTime;
    private String uTime;
}