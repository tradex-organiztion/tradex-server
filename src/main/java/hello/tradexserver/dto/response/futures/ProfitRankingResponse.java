package hello.tradexserver.dto.response.futures;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "거래 페어별 손익 랭킹 데이터")
public class ProfitRankingResponse {

    @Schema(description = "페어별 손익 랭킹 목록")
    private List<PairProfit> rankings;

    @Data
    @Builder
    @Schema(description = "페어별 손익 데이터")
    public static class PairProfit {

        @Schema(description = "순위", example = "1")
        private int rank;

        @Schema(description = "거래 페어", example = "BTCUSDT")
        private String symbol;

        @Schema(description = "총 손익 (USDT)", example = "1500.50")
        private BigDecimal totalPnl;

        @Schema(description = "거래 횟수", example = "25")
        private int tradeCount;

        @Schema(description = "승률 (%)", example = "72.0")
        private BigDecimal winRate;
    }
}
