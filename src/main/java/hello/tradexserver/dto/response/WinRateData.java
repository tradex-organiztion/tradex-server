package hello.tradexserver.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WinRateData {
    private int totalWins;
    private int totalLosses;
    private BigDecimal winRate;

    public static WinRateData of(int wins, int losses, BigDecimal rate) {
        return WinRateData.builder()
                .totalWins(wins)
                .totalLosses(losses)
                .winRate(rate)
                .build();
    }
}

