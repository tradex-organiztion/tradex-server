package hello.tradexserver.openApi.rest.dto;

import lombok.Data;

@Data
public class BybitOrderHistory {
    private String orderId;
    private String orderLinkId;
    private String symbol;
    private String price;
    private String qty;
    private String side;
    private String orderStatus;
    private String avgPrice;
    private String leavesQty;
    private String cumExecQty;
    private String cumExecValue;
    private String cumExecFee;
    private String orderType;
    private String stopOrderType;
    private String triggerPrice;
    private String takeProfit;
    private String stopLoss;
    private Boolean reduceOnly;
    private Integer positionIdx;
    private String createdTime;
    private String updatedTime;
}