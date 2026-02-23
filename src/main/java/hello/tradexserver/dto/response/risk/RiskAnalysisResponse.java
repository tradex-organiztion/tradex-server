package hello.tradexserver.dto.response.risk;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RiskAnalysisResponse {

    private int totalTrades;

    private EntryRiskResponse entryRisk;

    private ExitRiskResponse exitRisk;

    private PositionManagementRiskResponse positionManagementRisk;

    private TimeRiskResponse timeRisk;

    private EmotionalRiskResponse emotionalRisk;

    public static RiskAnalysisResponse empty() {
        return RiskAnalysisResponse.builder()
                .totalTrades(0)
                .entryRisk(EntryRiskResponse.builder()
                        .unplannedEntryCount(0).unplannedEntryRate(null)
                        .unplannedEntryWinRate(null).plannedEntryWinRate(null)
                        .emotionalReEntryCount(0).emotionalReEntryRate(null)
                        .impulsiveTradeCount(0).impulsiveTradeRate(null)
                        .build())
                .exitRisk(ExitRiskResponse.builder()
                        .slViolationCount(0).slViolationRate(null)
                        .avgSlDelay(null)
                        .earlyTpCount(0).earlyTpRate(null)
                        .build())
                .positionManagementRisk(PositionManagementRiskResponse.builder()
                        .avgRrRatio(null)
                        .averagingDownCount(0).averagingDownRate(null)
                        .build())
                .timeRisk(TimeRiskResponse.builder()
                        .hourlyWinRates(null)
                        .uptrendWinRate(null).downtrendWinRate(null).sidewaysWinRate(null)
                        .build())
                .emotionalRisk(EmotionalRiskResponse.builder()
                        .emotionalTradeCount(0).emotionalTradeRate(null)
                        .overconfidentEntryCount(0).overconfidentEntryRate(null)
                        .immediateReverseCount(0).immediateReverseRate(null)
                        .build())
                .build();
    }
}
