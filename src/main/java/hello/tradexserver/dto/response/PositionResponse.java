package hello.tradexserver.dto.response;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PositionResponse {

    private Long positionId;
    private Long userId;
    private ExchangeName exchangeName;
    private String symbol;
    private PositionSide side;
    private LocalDateTime entryTime;
    private BigDecimal avgEntryPrice;
    private Integer leverage;
    private LocalDateTime exitTime;
    private BigDecimal avgExitPrice;
    private BigDecimal realizedPnl;
    private BigDecimal targetPrice;
    private BigDecimal stopLossPrice;
    private BigDecimal openFee;
    private BigDecimal closedFee;
    private MarketCondition marketCondition;
    private PositionStatus status;
    private BigDecimal roi;
    private LocalDateTime createdAt;

    public static PositionResponse from(Position position) {
        return PositionResponse.builder()
                .positionId(position.getId())
                .userId(position.getUser().getId())
                .exchangeName(position.getExchangeName())
                .symbol(position.getSymbol())
                .side(position.getSide())
                .entryTime(position.getEntryTime())
                .avgEntryPrice(position.getAvgEntryPrice())
                .leverage(position.getLeverage())
                .exitTime(position.getExitTime())
                .avgExitPrice(position.getAvgExitPrice())
                .realizedPnl(position.getRealizedPnl())
                .targetPrice(position.getTargetPrice())
                .stopLossPrice(position.getStopLossPrice())
                .openFee(position.getOpenFee())
                .closedFee(position.getClosedFee())
                .marketCondition(position.getMarketCondition())
                .status(position.getStatus())
                .roi(position.getRoi())
                .createdAt(position.getCreatedAt())
                .build();
    }
}