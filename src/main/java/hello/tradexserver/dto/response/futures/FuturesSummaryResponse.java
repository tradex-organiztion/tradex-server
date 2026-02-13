package hello.tradexserver.dto.response.futures;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "선물 거래 요약 데이터")
public class FuturesSummaryResponse {

    @Schema(description = "총 손익 (USDT)", example = "2500.50")
    private BigDecimal totalPnl;

    @Schema(description = "총 거래 규모 (USDT)", example = "150000.00")
    private BigDecimal totalVolume;

    @Schema(description = "승률 (%)", example = "65.5")
    private BigDecimal winRate;

    @Schema(description = "승리 횟수", example = "42")
    private int winCount;

    @Schema(description = "패배 횟수", example = "22")
    private int lossCount;

    @Schema(description = "총 거래 횟수", example = "64")
    private int totalTradeCount;

    @Schema(description = "손익 시계열 차트 데이터")
    private List<PnlChartData> pnlChart;

    @Data
    @Builder
    @Schema(description = "손익 차트 데이터")
    public static class PnlChartData {

        @Schema(description = "날짜", example = "2024-01-01")
        private LocalDate date;

        @Schema(description = "일일 손익 (USDT)", example = "150.50")
        private BigDecimal pnl;

        @Schema(description = "누적 손익 (USDT)", example = "850.50")
        private BigDecimal cumulativePnl;
    }
}
