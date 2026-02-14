package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitOrderData {
    private String category;
    private String orderId;
    private String orderLinkId;
    private String symbol;
    private String side;
    private Integer positionIdx;
    private String orderStatus;
    private String orderType;
    private String avgPrice;
    private String cumExecQty;
    private String cumExecValue;
    private String cumExecFee;
    private String closedPnl;
    private Boolean reduceOnly;
    private String createdTime;
    private String updatedTime;
}