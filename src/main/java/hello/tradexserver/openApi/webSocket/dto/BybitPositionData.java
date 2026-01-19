package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitPositionData {
    private Integer positionIdx;
    private Integer tradeMode;
    private Integer riskId;
    private String riskLimitValue;
    private String symbol;
    private String side;
    private String size;
    private String entryPrice;
    private String leverage;
    private String breakEvenPrice;
    private String positionValue;
    private String positionBalance;
    private String markPrice;
    private String positionIM;
    private String positionMM;
    private String takeProfit;
    private String stopLoss;
    private String trailingStop;
    private String unrealisedPnl;
    private String curRealisedPnl;
    private String cumRealisedPnl;
    private String sessionAvgPrice;
    private String createdTime;
    private String updatedTime;
    private String tpslMode;
    private String liqPrice;
    private String bustPrice;
    private String category;
    private String positionStatus;
    private Integer adlRankIndicator;
    private Integer autoAddMargin;
    private String leverageSysUpdatedTime;
    private String mmrSysUpdatedTime;
    private Long seq;
    private Boolean isReduceOnly;
}