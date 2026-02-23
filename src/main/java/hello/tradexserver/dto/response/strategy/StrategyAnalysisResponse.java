package hello.tradexserver.dto.response.strategy;

import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class StrategyAnalysisResponse {

    private int totalTrades;

    /** 승률 내림차순 정렬된 전략 패턴 목록 */
    private List<StrategyItem> strategies;

    @Getter
    @Builder
    public static class StrategyItem {
        private List<String> indicators;
        private List<String> technicalAnalyses;
        private List<String> timeframes;
        private PositionSide side;
        private MarketCondition marketCondition;

        private int totalTrades;
        private int winCount;
        private int lossCount;

        /** 승률 (%) */
        private BigDecimal winRate;

        /** 평균 실현 손익 (USDT) */
        private BigDecimal avgProfit;

        /** 평균 R/R 비율 (계획 TP-Entry / Entry-SL 기준) */
        private BigDecimal avgRrRatio;
    }
}
