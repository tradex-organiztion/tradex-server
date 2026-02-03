package hello.tradexserver.dto.response.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "월간 총 자산 추이 데이터")
public class AssetHistoryResponse {

    @Schema(description = "조회 연도", example = "2024")
    private int year;

    @Schema(description = "조회 월", example = "1")
    private int month;

    @Schema(description = "월초 자산 (USDT)", example = "10000.00")
    private BigDecimal startAsset;

    @Schema(description = "월말 자산 (USDT)", example = "11500.00")
    private BigDecimal endAsset;

    @Schema(description = "월간 수익률 (%)", example = "15.0")
    private BigDecimal monthlyReturnRate;

    @Schema(description = "일별 자산 추이 데이터")
    private List<DailyAsset> dailyAssets;

    @Data
    @Builder
    @Schema(description = "일별 자산 데이터")
    public static class DailyAsset {

        @Schema(description = "날짜", example = "2024-01-01")
        private LocalDate date;

        @Schema(description = "총 자산 (USDT)", example = "10150.50")
        private BigDecimal totalAsset;

        @Schema(description = "일일 수익률 (%)", example = "1.5")
        private BigDecimal dailyReturnRate;
    }
}
