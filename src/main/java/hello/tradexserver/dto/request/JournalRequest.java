package hello.tradexserver.dto.request;

import hello.tradexserver.domain.enums.MarketCondition;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JournalRequest {

    private BigDecimal plannedTargetPrice;
    private BigDecimal plannedStopLoss;
    private String entryScenario;
    private String exitReview;
    private List<String> indicators;
    private List<String> timeframes;
    private List<String> technicalAnalyses;
    private MarketCondition marketCondition;
}