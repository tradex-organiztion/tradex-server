package hello.tradexserver.dto.response.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "자산 분포 데이터")
public class AssetDistributionResponse {

    @Schema(description = "순자산 총액 (USDT)", example = "10000.00")
    private BigDecimal totalNetAsset;

    @Schema(description = "보유 코인 목록")
    private List<CoinBalance> coins;

    @Data
    @Builder
    @Schema(description = "코인별 잔고 데이터")
    public static class CoinBalance {

        @Schema(description = "코인 심볼", example = "BTC")
        private String coin;

        @Schema(description = "보유 수량", example = "0.5")
        private BigDecimal quantity;

        @Schema(description = "평가 금액 (USDT)", example = "21500.00")
        private BigDecimal usdValue;

        @Schema(description = "전체 자산 대비 비중 (%)", example = "45.5")
        private BigDecimal percentage;
    }
}
