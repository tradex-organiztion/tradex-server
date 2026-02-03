package hello.tradexserver.dto.response.futures;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionSide;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "종료 포지션 상세 데이터")
public class ClosedPositionResponse {

    @Schema(description = "포지션 ID", example = "1")
    private Long id;

    @Schema(description = "거래 페어", example = "BTCUSDT")
    private String symbol;

    @Schema(description = "포지션 방향", example = "LONG")
    private PositionSide side;

    @Schema(description = "수량", example = "0.1")
    private BigDecimal size;

    @Schema(description = "레버리지", example = "10")
    private Integer leverage;

    @Schema(description = "진입가", example = "43500.00")
    private BigDecimal entryPrice;

    @Schema(description = "종료가", example = "44000.00")
    private BigDecimal exitPrice;

    @Schema(description = "손익 (USDT)", example = "50.00")
    private BigDecimal pnl;

    @Schema(description = "결과 (WIN/LOSS)", example = "WIN")
    private String result;

    @Schema(description = "거래 규모 (USDT)", example = "4350.00")
    private BigDecimal volume;

    @Schema(description = "총 수수료 (USDT)", example = "2.61")
    private BigDecimal totalFee;

    @Schema(description = "진입 시간")
    private LocalDateTime entryTime;

    @Schema(description = "종료 시간")
    private LocalDateTime exitTime;

    public static ClosedPositionResponse from(Position position) {
        BigDecimal volume = BigDecimal.ZERO;
        if (position.getAvgEntryPrice() != null && position.getClosedSize() != null) {
            volume = position.getAvgEntryPrice()
                    .multiply(position.getClosedSize());
            if (position.getLeverage() != null) {
                volume = volume.multiply(BigDecimal.valueOf(position.getLeverage()));
            }
        }

        BigDecimal totalFee = BigDecimal.ZERO;
        if (position.getOpenFee() != null) {
            totalFee = totalFee.add(position.getOpenFee());
        }
        if (position.getClosedFee() != null) {
            totalFee = totalFee.add(position.getClosedFee());
        }

        String result = "LOSS";
        if (position.getRealizedPnl() != null && position.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0) {
            result = "WIN";
        }

        return ClosedPositionResponse.builder()
                .id(position.getId())
                .symbol(position.getSymbol())
                .side(position.getSide())
                .size(position.getClosedSize())
                .leverage(position.getLeverage())
                .entryPrice(position.getAvgEntryPrice())
                .exitPrice(position.getAvgExitPrice())
                .pnl(position.getRealizedPnl())
                .result(result)
                .volume(volume.setScale(2, RoundingMode.HALF_UP))
                .totalFee(totalFee.setScale(8, RoundingMode.HALF_UP))
                .entryTime(position.getEntryTime())
                .exitTime(position.getExitTime())
                .build();
    }
}
