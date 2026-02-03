package hello.tradexserver.dto.response.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "기간별 누적 손익 시계열 데이터")
public class CumulativeProfitResponse {

    @Schema(description = "조회 시작일", example = "2024-01-01")
    private LocalDate startDate;

    @Schema(description = "조회 종료일", example = "2024-01-07")
    private LocalDate endDate;

    @Schema(description = "총 누적 손익 (USDT)", example = "1250.50")
    private BigDecimal totalProfit;

    @Schema(description = "총 누적 손익률 (%)", example = "12.5")
    private BigDecimal totalProfitRate;

    @Schema(description = "일별 누적 손익 시계열 데이터")
    private List<DailyProfit> dailyProfits;

    @Data
    @Builder
    @Schema(description = "일별 누적 손익 데이터")
    public static class DailyProfit {

        @Schema(description = "날짜", example = "2024-01-01")
        private LocalDate date;

        @Schema(description = "해당일 손익 (USDT)", example = "150.50")
        private BigDecimal profit;

        @Schema(description = "누적 손익 (USDT)", example = "350.50")
        private BigDecimal cumulativeProfit;

        @Schema(description = "누적 손익률 (%)", example = "3.5")
        private BigDecimal cumulativeProfitRate;
    }
}
