package hello.tradexserver.dto.response;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class JournalDetailResponse {

    private Long journalId;
    private Long positionId;

    // 저널 내용
    private BigDecimal plannedTargetPrice;
    private BigDecimal plannedStopLoss;
    private String entryScenario;
    private String exitReview;

    // 포지션
    private ExchangeName exchangeName;
    private String symbol;
    private PositionSide side;
    private Integer leverage;
    private PositionStatus positionStatus;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private BigDecimal avgEntryPrice;
    private BigDecimal avgExitPrice;
    private BigDecimal realizedPnl;
    private BigDecimal openFee;
    private BigDecimal closedFee;
    private BigDecimal targetPrice;
    private BigDecimal stopLossPrice;

    // 오더 목록
    private List<OrderResponse> orders;

    private LocalDateTime createdAt;

    public static JournalDetailResponse from(TradingJournal journal, List<OrderResponse> orders) {
        Position position = journal.getPosition();
        return JournalDetailResponse.builder()
                .journalId(journal.getId())
                .positionId(position.getId())
                .plannedTargetPrice(journal.getPlannedTargetPrice())
                .plannedStopLoss(journal.getPlannedStopLoss())
                .entryScenario(journal.getEntryScenario())
                .exitReview(journal.getExitReview())
                .exchangeName(position.getExchangeName())
                .symbol(position.getSymbol())
                .side(position.getSide())
                .leverage(position.getLeverage())
                .positionStatus(position.getStatus())
                .entryTime(position.getEntryTime())
                .exitTime(position.getExitTime())
                .avgEntryPrice(position.getAvgEntryPrice())
                .avgExitPrice(position.getAvgExitPrice())
                .realizedPnl(position.getRealizedPnl())
                .openFee(position.getOpenFee())
                .closedFee(position.getClosedFee())
                .targetPrice(position.getTargetPrice())
                .stopLossPrice(position.getStopLossPrice())
                .orders(orders)
                .createdAt(journal.getCreatedAt())
                .build();
    }
}