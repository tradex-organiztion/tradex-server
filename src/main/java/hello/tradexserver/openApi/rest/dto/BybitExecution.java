package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitExecution {
    private String execId;
    private String orderId;
    private String symbol;
    private String side;        // Buy / Sell
    private String execPrice;
    private String execQty;
    private String execFee;
    private String feeCurrency;
    private String execTime;    // epoch millis
    private String closedPnl;
    private String orderType;
    private String execType;    // Trade / Funding 등
}
