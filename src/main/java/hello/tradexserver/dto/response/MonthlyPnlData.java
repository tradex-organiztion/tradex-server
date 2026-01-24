package hello.tradexserver.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MonthlyPnlData {
    private BigDecimal thisMonthPnl;
    private BigDecimal lastMonthFinalPnl;
    private BigDecimal achievementRate;

    public static MonthlyPnlData of(BigDecimal thisMonth, BigDecimal lastMonth, BigDecimal rate) {
        return MonthlyPnlData.builder()
                .thisMonthPnl(thisMonth)
                .lastMonthFinalPnl(lastMonth)
                .achievementRate(rate)
                .build();
    }
}
