package hello.tradexserver.dto.response.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "포트폴리오 요약 데이터")
public class PortfolioSummaryResponse {

    @Schema(description = "총 자산 (USDT)", example = "10000.00")
    private BigDecimal totalAsset;

    @Schema(description = "오늘의 손익 (USDT)", example = "150.50")
    private BigDecimal todayPnl;

    @Schema(description = "오늘의 손익 변동률 (%)", example = "1.52")
    private BigDecimal todayPnlRate;

    @Schema(description = "주간 손익 (USDT)", example = "520.30")
    private BigDecimal weeklyPnl;

    @Schema(description = "주간 손익 변동률 (%)", example = "5.45")
    private BigDecimal weeklyPnlRate;
}
