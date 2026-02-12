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

@Getter
@Builder
public class JournalSummaryResponse {

    private Long journalId;
    private Long positionId;
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
    private LocalDateTime createdAt;

    public static JournalSummaryResponse from(TradingJournal journal) {
        Position position = journal.getPosition();
        return JournalSummaryResponse.builder()
                .journalId(journal.getId())
                .positionId(position.getId())
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
                .createdAt(journal.getCreatedAt())
                .build();
    }
}