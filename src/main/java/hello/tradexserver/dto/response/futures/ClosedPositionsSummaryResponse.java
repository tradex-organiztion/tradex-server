package hello.tradexserver.dto.response.futures;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "종료 포지션 요약 데이터")
public class ClosedPositionsSummaryResponse {

    @Schema(description = "총 종료 주문 수", example = "64")
    private int totalClosedCount;

    @Schema(description = "승률 (%)", example = "65.5")
    private BigDecimal winRate;

    @Schema(description = "롱 포지션 총 손익 (USDT)", example = "1800.50")
    private BigDecimal longPnl;

    @Schema(description = "롱 포지션 거래 수", example = "35")
    private int longCount;

    @Schema(description = "숏 포지션 총 손익 (USDT)", example = "700.00")
    private BigDecimal shortPnl;

    @Schema(description = "숏 포지션 거래 수", example = "29")
    private int shortCount;
}
