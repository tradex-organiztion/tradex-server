package hello.tradexserver.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class JournalStatsResponse {

    private int totalTrades;
    private int winCount;
    private int lossCount;
    private BigDecimal winRate;
    private BigDecimal avgPnl;
    private BigDecimal avgRoi;
}
