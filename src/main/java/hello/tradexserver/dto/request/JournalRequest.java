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

    // 사전 시나리오
    private List<String> indicators;
    private List<String> timeframes;
    private List<String> technicalAnalyses;
    private BigDecimal targetPrice;
    private BigDecimal stopLoss;
    private String entryReason;
    private String targetScenario;

    // 매매 후 복기
    private String chartScreenshotUrl;
    private String reviewContent;

    // 매매원칙 준수 체크
    private List<PrincipleCheckRequest> principleChecks;

    private MarketCondition marketCondition;
}