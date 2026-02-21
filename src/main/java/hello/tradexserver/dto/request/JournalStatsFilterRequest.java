package hello.tradexserver.dto.request;

import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JournalStatsFilterRequest {

    private List<String> indicators;
    private List<String> timeframes;
    private List<String> technicalAnalyses;
    private String tradingStyle;             // "SCALPING" | "SWING" | null
    private PositionSide positionSide;
    private MarketCondition marketCondition;
}
