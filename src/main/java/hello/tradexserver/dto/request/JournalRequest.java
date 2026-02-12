package hello.tradexserver.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JournalRequest {

    private BigDecimal plannedTargetPrice;
    private BigDecimal plannedStopLoss;
    private String entryScenario;
    private String exitReview;
}