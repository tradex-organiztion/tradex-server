package hello.tradexserver.dto.response.strategy;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PerformanceResponse {

    /** 그래프 단위: DAILY | WEEKLY | MONTHLY */
    private String granularity;

    private List<DataPoint> data;

    private Summary summary;

    @Getter
    @Builder
    public static class DataPoint {
        /** DAILY: "2026-02-15" / WEEKLY: "2026-W07" / MONTHLY: "2026-02" */
        private String label;
        private String startDate;
        private String endDate;
        private BigDecimal pnl;
        private BigDecimal cumulativePnl;
        private int tradeCount;
        private int winCount;
        private int lossCount;
    }

    @Getter
    @Builder
    public static class Summary {
        private int maxWinStreak;
        private int maxLossStreak;
        private BigDecimal totalPnl;
    }
}