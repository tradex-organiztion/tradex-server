package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.MappingStatus;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
public class Position extends BaseTimeEntity {

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
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionSide side;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal avgEntryPrice; // 평균 진입가

    @Column(precision = 20, scale = 8)
    private BigDecimal currentSize;

    @Column(precision = 20, scale = 8)
    private BigDecimal openFee;

    @Column(precision = 20, scale = 8)
    private BigDecimal closedFee;

    @Column(precision = 20, scale = 8)
    private BigDecimal closedSize;

    private Integer leverage;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    private LocalDateTime exchangeUpdateTime;

    @Column(precision = 20, scale = 8)
    private BigDecimal avgExitPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal realizedPnl;

    @Column(precision = 20, scale = 8)
    private BigDecimal targetPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal stopLossPrice;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MarketCondition marketCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private MappingStatus mappingStatus = MappingStatus.NONE;

    @Column(columnDefinition = "TEXT")
    private String nextPageCursor;

    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @OneToOne(mappedBy = "position", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private TradingJournal tradingJournal;

    public boolean isClosed() {
        return this.status == PositionStatus.CLOSED;
    }

    public void updateFromWebSocket(BigDecimal avgEntryPrice, BigDecimal currentSize,
                                     Integer leverage, BigDecimal realizedPnl) {
        this.avgEntryPrice = avgEntryPrice;
        this.currentSize = currentSize;
        if (leverage != null) this.leverage = leverage;
        this.realizedPnl = realizedPnl;
    }

    /**
     * 포지션 종료 감지 → CLOSED + 매핑 시작
     */
    public void closingPosition(LocalDateTime exitTime) {
        this.exitTime = exitTime;
        this.status = PositionStatus.CLOSED;
        this.mappingStatus = MappingStatus.IN_PROGRESS;
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

    /**
     * 오더 매핑 완료 → 포지션 계산 결과 반영
     */
    public void applyMappingResult(BigDecimal avgExitPrice, BigDecimal realizedPnl,
                                    BigDecimal closedFee, BigDecimal openFee,
                                    BigDecimal closedSize) {
        this.avgExitPrice = avgExitPrice;
        this.realizedPnl = realizedPnl;
        this.closedFee = closedFee;
        this.openFee = openFee;
        this.closedSize = closedSize;
        this.currentSize = BigDecimal.ZERO;
        this.mappingStatus = MappingStatus.MAPPED;
    }

    /**
     * 오더 매핑 실패
     */
    public void failMapping() {
        this.mappingStatus = MappingStatus.FAILED;
    }
}