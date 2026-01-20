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
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private ExchangeName exchangeName;

    @Column(nullable = false, length = 50)
    private String symbol; // 자산 종목 ex) BTCUSDT, ETHUSDT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionSide side; // 포지션 방향 Buy(Long), Sell(Short)

    @Column(nullable = false)
    private LocalDateTime entryTime; // 포지션이 처음 생성된 타임스탬프

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal avgEntryPrice; // 평균 진입 가격

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal closedSize; // 오더 수량

    private Integer leverage;

    private LocalDateTime exitTime; // 청산 날짜 및 시간

    @Column(precision = 20, scale = 8)
    private BigDecimal avgExitPrice; // 청산 가격

    @Column(precision = 20, scale = 8)
    private BigDecimal realizedPnl; // 거래 손익

    @Column(precision = 20, scale = 8)
    private BigDecimal targetPrice; // 익절가

    @Column(precision = 20, scale = 8)
    private BigDecimal stopLossPrice; // 손절가

    @Column(precision = 20, scale = 8)
    private BigDecimal openFee; // 진입 수수료

    @Column(precision = 20, scale = 8)
    private BigDecimal closedFee; // 청산 수수료

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MarketCondition marketCondition;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private PositionStatus status = PositionStatus.OPEN;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String nextPageCursor;

    @OneToMany(mappedBy = "position")
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @OneToOne(mappedBy = "position", fetch = FetchType.LAZY)
    private TradingJournal tradingJournal;
}