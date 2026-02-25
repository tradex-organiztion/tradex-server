package hello.tradexserver.dto.response;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class JournalDetailResponse {

    private Long journalId;
    private Long positionId;

    // 사전 시나리오
    private List<String> indicators;
    private List<String> timeframes;
    private List<String> technicalAnalyses;
    private BigDecimal targetPrice;
    private BigDecimal stopLoss;
    private String entryReason;
    private String targetScenario;

    // 매매 후 복기
    private String chartScreenshotUrl;
    private String reviewContent;

    // 매매원칙 준수 체크
    private List<PrincipleCheckResponse> principleChecks;

    // 포지션
    private ExchangeName exchangeName;
    private String symbol;
    private PositionSide side;
    private MarketCondition marketCondition;
    private Integer leverage;
    private PositionStatus positionStatus;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private BigDecimal avgEntryPrice;
    private BigDecimal avgExitPrice;
    private BigDecimal realizedPnl;
    private BigDecimal openFee;
    private BigDecimal closedFee;
    private BigDecimal exchangeTargetPrice;
    private BigDecimal exchangeStopLossPrice;
    private BigDecimal roi;

    // 오더 목록
    private List<OrderResponse> orders;

    private LocalDateTime createdAt;

    public static JournalDetailResponse from(TradingJournal journal, List<OrderResponse> orders,
                                             List<PrincipleCheckResponse> principleChecks) {
        Position position = journal.getPosition();
        return JournalDetailResponse.builder()
                .journalId(journal.getId())
                .positionId(position.getId())
                .indicators(journal.getIndicators())
                .timeframes(journal.getTimeframes())
                .technicalAnalyses(journal.getTechnicalAnalyses())
                .targetPrice(journal.getTargetPrice())
                .stopLoss(journal.getStopLoss())
                .entryReason(journal.getEntryReason())
                .targetScenario(journal.getTargetScenario())
                .chartScreenshotUrl(journal.getChartScreenshotUrl())
                .reviewContent(journal.getReviewContent())
                .principleChecks(principleChecks)
                .exchangeName(position.getExchangeName())
                .symbol(position.getSymbol())
                .side(position.getSide())
                .marketCondition(position.getMarketCondition())
                .leverage(position.getLeverage())
                .positionStatus(position.getStatus())
                .entryTime(position.getEntryTime())
                .exitTime(position.getExitTime())
                .avgEntryPrice(position.getAvgEntryPrice())
                .avgExitPrice(position.getAvgExitPrice())
                .realizedPnl(position.getRealizedPnl())
                .openFee(position.getOpenFee())
                .closedFee(position.getClosedFee())
                .exchangeTargetPrice(position.getTargetPrice())
                .exchangeStopLossPrice(position.getStopLossPrice())
                .roi(position.getRoi())
                .orders(orders)
                .createdAt(journal.getCreatedAt())
                .build();
    }
}