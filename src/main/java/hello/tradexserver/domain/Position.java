package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "positions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position extends BaseTimeEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_api_key_id")
    private ExchangeApiKey exchangeApiKey;

    @Enumerated(EnumType.STRING)
    private ExchangeName exchangeName;

    @Column(nullable = false, length = 50)
    private String symbol; // 자산 종목 ex) BTCUSDT, ETHUSDT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionSide side; // 포지션 방향 Buy(Long), Sell(Short)

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal avgEntryPrice; // 평균 진입 가격

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal currentSize;

    @Column(precision = 20, scale = 8)
    private BigDecimal openFee;

    @Column(precision = 20, scale = 8)
    private BigDecimal closedFee;

    private Integer leverage;

    @Column(nullable = false)
    private LocalDateTime entryTime; // 포지션이 처음 생성된 타임스탬프

    private LocalDateTime exitTime; // 청산 날짜 및 시간

    private LocalDateTime exchangeUpdateTime;

    @Column(precision = 20, scale = 8)
    private BigDecimal avgExitPrice; // 청산 가격

    @Column(precision = 20, scale = 8)
    private BigDecimal realizedPnl; // 거래 손익

    @Column(precision = 20, scale = 8)
    private BigDecimal targetPrice; // 익절가

    @Column(precision = 20, scale = 8)
    private BigDecimal stopLossPrice; // 손절가

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MarketCondition marketCondition;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PositionStatus status;

    @Column(columnDefinition = "TEXT")
    private String nextPageCursor;

    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @OneToOne(mappedBy = "position", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private TradingJournal tradingJournal;

    public void updateStatus(PositionStatus newStatus) {
        this.status = newStatus;
    }

    public void updateFromWebSocket(BigDecimal avgEntryPrice, BigDecimal currentSize,
                                     Integer leverage, BigDecimal realizedPnl) {
        this.avgEntryPrice = avgEntryPrice;
        this.currentSize = currentSize;
        if (leverage != null) this.leverage = leverage;
        this.realizedPnl = realizedPnl;
    }

    public void closingPosition(LocalDateTime exitTime, PositionStatus status) {
        this.exitTime = exitTime;
        this.status = status;
    }

    public void update(BigDecimal avgEntryPrice, BigDecimal avgExitPrice,
                       BigDecimal currentSize, Integer leverage, BigDecimal targetPrice,
                       BigDecimal stopLossPrice, LocalDateTime entryTime, LocalDateTime exitTime) {
        if (avgEntryPrice != null) this.avgEntryPrice = avgEntryPrice;
        if (avgExitPrice != null) this.avgExitPrice = avgExitPrice;
        if (currentSize != null) this.currentSize = currentSize;
        if (leverage != null) this.leverage = leverage;
        if (targetPrice != null) this.targetPrice = targetPrice;
        if (stopLossPrice != null) this.stopLossPrice = stopLossPrice;
        if (entryTime != null) this.entryTime = entryTime;
        if (exitTime != null) this.exitTime = exitTime;
    }

    public boolean isClosed() {
        return this.status == PositionStatus.CLOSED_MAPPED || this.status == PositionStatus.CLOSED;
    }

    public void applyMappingResult(BigDecimal avgExitPrice, BigDecimal realizedPnl,
                                    BigDecimal closedFee, BigDecimal openFee) {
        this.avgExitPrice = avgExitPrice;
        this.realizedPnl = realizedPnl;
        this.closedFee = closedFee;
        this.openFee = openFee;
        this.currentSize = BigDecimal.ZERO;
        this.status = PositionStatus.CLOSED_MAPPED;
    }
}