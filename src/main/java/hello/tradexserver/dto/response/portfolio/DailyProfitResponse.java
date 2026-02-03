package hello.tradexserver.dto.response.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "월간 일별 손익 데이터 (캘린더용)")
public class DailyProfitResponse {

    @Schema(description = "조회 연도", example = "2024")
    private int year;

    @Schema(description = "조회 월", example = "1")
    private int month;

    @Schema(description = "월간 총 손익 (USDT)", example = "1500.00")
    private BigDecimal monthlyTotalPnl;

    @Schema(description = "투자 대비 수익률 (%)", example = "15.0")
    private BigDecimal monthlyReturnRate;

    @Schema(description = "승리 거래 수", example = "25")
    private int totalWinCount;

    @Schema(description = "패배 거래 수", example = "10")
    private int totalLossCount;

    @Schema(description = "캘린더용 일별 손익 데이터")
    private List<DailyPnl> dailyPnlList;

    @Data
    @Builder
    @Schema(description = "일별 손익 데이터")
    public static class DailyPnl {

        @Schema(description = "날짜", example = "2024-01-01")
        private LocalDate date;

        @Schema(description = "일일 손익 (USDT)", example = "150.50")
        private BigDecimal pnl;

        @Schema(description = "승리 횟수", example = "3")
        private int winCount;

        @Schema(description = "패배 횟수", example = "1")
        private int lossCount;
    }
}
