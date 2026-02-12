package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitClosedPnl {
    private String symbol;
    private String orderId;
    private String side;
    private String qty;
    private String orderPrice;
    private String orderType;
    private String execType;
    private String closedSize;
    private String cumEntryValue;
    private String avgEntryPrice;
    private String cumExitValue;
    private String avgExitPrice;
    private String closedPnl;
    private String fillCount;
    private String leverage;
    private String openFee;
    private String closeFee;
    private String createdTime;
    private String updatedTime;
}