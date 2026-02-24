package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.DataSource;
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
import java.math.RoundingMode;
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
    @Column(nullable = false, length = 10)
    @Builder.Default
    private DataSource dataSource = DataSource.EXCHANGE;

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

    /**
     * ROI (%) = realizedPnl × leverage / (avgEntryPrice × closedSize) × 100
     * CLOSED이고 필요한 값이 모두 있을 때만 계산
     */
    public BigDecimal getRoi() {
        if (realizedPnl == null || leverage == null || avgEntryPrice == null
                || closedSize == null || closedSize.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal denominator = avgEntryPrice.multiply(closedSize);
        return realizedPnl
                .multiply(BigDecimal.valueOf(leverage))
                .divide(denominator, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
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
     * 오더 매핑 완료 → 포지션 계산 결과 반영 (수동 재계산용)
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

    // ============ 오더 기반 포지션 재구성용 도메인 메서드 ============

    /**
     * 진입 오더 반영: 가중평균 진입가 계산 + currentSize 증가
     */
    public void addEntry(BigDecimal price, BigDecimal qty, BigDecimal fee) {
        BigDecimal oldNotional = this.avgEntryPrice.multiply(this.currentSize);
        BigDecimal newNotional = price.multiply(qty);
        this.currentSize = this.currentSize.add(qty);
        this.avgEntryPrice = oldNotional.add(newNotional)
                .divide(this.currentSize, 8, RoundingMode.HALF_UP);
        this.openFee = (this.openFee != null ? this.openFee : BigDecimal.ZERO)
                .add(fee != null ? fee : BigDecimal.ZERO);
    }

    /**
     * 부분 청산 반영: closedSize, avgExitPrice, closedFee, realizedPnl 누적
     */
    public void addPartialClose(BigDecimal price, BigDecimal qty, BigDecimal fee, BigDecimal pnl) {
        BigDecimal prevClosedSize = this.closedSize != null ? this.closedSize : BigDecimal.ZERO;
        BigDecimal prevExitNotional = (this.avgExitPrice != null ? this.avgExitPrice : BigDecimal.ZERO)
                .multiply(prevClosedSize);

        this.closedSize = prevClosedSize.add(qty);
        this.avgExitPrice = prevExitNotional.add(price.multiply(qty))
                .divide(this.closedSize, 8, RoundingMode.HALF_UP);
        this.currentSize = this.currentSize.subtract(qty);
        this.closedFee = (this.closedFee != null ? this.closedFee : BigDecimal.ZERO)
                .add(fee != null ? fee : BigDecimal.ZERO);
        this.realizedPnl = (this.realizedPnl != null ? this.realizedPnl : BigDecimal.ZERO)
                .add(pnl != null ? pnl : BigDecimal.ZERO);
    }

    /**
     * 포지션 완전 종료: CLOSED + MAPPED (오더가 이미 매핑된 상태)
     */
    public void completeClose(LocalDateTime exitTime) {
        this.exitTime = exitTime;
        this.currentSize = BigDecimal.ZERO;
        this.status = PositionStatus.CLOSED;
        this.mappingStatus = MappingStatus.MAPPED;
    }

    public void updateLeverage(Integer leverage) {
        if (leverage != null) this.leverage = leverage;
    }

    public void updateTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public void updateStopLossPrice(BigDecimal stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }

    public void updateMarketCondition(MarketCondition marketCondition) {
        this.marketCondition = marketCondition;
    }
}