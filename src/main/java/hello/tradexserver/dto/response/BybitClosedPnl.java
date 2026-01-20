package hello.tradexserver.dto.response;

import lombok.Data;

@Data
public class BybitClosedPnl {
    private String symbol;
    private String side;
    private String totalOpenFee;
    private String totalCloseFee;
    private String qty;
    private Long closeTime;
    private String avgExitPrice;
    private Long openTime;
    private String avgEntryPrice;
    private String totalPnl;
}
