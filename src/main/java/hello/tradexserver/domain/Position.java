package hello.tradexserver.domain;

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
    private Long positionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String exchangeName;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionSide side;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal avgEntryPrice;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    private Integer leverage;

    private LocalDateTime exitTime;

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
    @Column(length = 20)
    @Builder.Default
    private PositionStatus status = PositionStatus.OPEN;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "position")
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @OneToOne(mappedBy = "position", fetch = FetchType.LAZY)
    private TradingJournal tradingJournal;
}