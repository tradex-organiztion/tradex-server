package hello.tradexserver.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "홈 화면 요약 데이터")
public class HomeScreenResponse {

    @Schema(description = "오늘 총 자산 (원)", example = "15000000")
    private BigDecimal todayTotalAsset;

    @Schema(description = "어제 총 자산 (원)", example = "14500000")
    private BigDecimal yesterdayTotalAsset;

    @Schema(description = "전일 대비 자산 증감률 (%)", example = "3.45")
    private BigDecimal assetChangeRate;

    @Schema(description = "이번 달 실현 손익 (원)", example = "500000")
    private BigDecimal thisMonthPnl;

    @Schema(description = "지난 달 최종 손익 (원)", example = "800000")
    private BigDecimal lastMonthFinalPnl;

    @Schema(description = "목표 달성률 (%) - 지난 달 대비 이번 달 수익 비율", example = "62.5")
    private BigDecimal achievementRate;

    @Schema(description = "최근 7일 승리 횟수", example = "12")
    private int totalWins;

    @Schema(description = "최근 7일 패배 횟수", example = "5")
    private int totalLosses;

    @Schema(description = "최근 7일 승률 (%)", example = "70.59")
    private BigDecimal winRate;

    @Schema(description = "최근 7일 일별 누적 손익 차트 데이터")
    private List<DailyPnlChartData> pnlChart;

    public static HomeScreenResponse of(AssetData asset, MonthlyPnlData monthlyPnl,
                                        WinRateData winRate, List<DailyPnlChartData> pnlChart) {
        return HomeScreenResponse.builder()
                .todayTotalAsset(asset.getCurrentTotalAsset())
                .yesterdayTotalAsset(asset.getYesterdayTotalAsset())
                .assetChangeRate(asset.getChangeRate())
                .thisMonthPnl(monthlyPnl.getThisMonthPnl())
                .lastMonthFinalPnl(monthlyPnl.getLastMonthFinalPnl())
                .achievementRate(monthlyPnl.getAchievementRate())
                .totalWins(winRate.getTotalWins())
                .totalLosses(winRate.getTotalLosses())
                .winRate(winRate.getWinRate())
                .pnlChart(pnlChart)
                .build();
    }
}
