package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "orders",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_exchange_order",
        columnNames = {"exchange_name", "exchange_order_id"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_api_key_id", nullable = false)
    private ExchangeApiKey exchangeApiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange_name", nullable = false, length = 20)
    private ExchangeName exchangeName;

    @Column(name = "exchange_order_id", nullable = false, length = 100)
    private String exchangeOrderId;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PositionEffect positionEffect;

    @Column(precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal filledPrice = BigDecimal.ZERO;

    @Column(precision = 20, scale = 8)
    private BigDecimal cumExecFee;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @Column(precision = 20, scale = 8)
    private BigDecimal realizedPnl; // 거래 손익

    @Column(nullable = false)
    private LocalDateTime orderTime;

    private LocalDateTime fillTime;

    private Integer positionIdx;  // hedge mode 구분용 (0=one-way, 1=buy-hedge, 2=sell-hedge)

    private String orderLinkId;   // 사용자 지정 주문 ID

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void assignToPosition(Position position) {
        this.position = position;
    }

    public void updateRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public void update(BigDecimal filledQuantity, BigDecimal filledPrice, BigDecimal cumExecFee,
                       BigDecimal realizedPnl, LocalDateTime orderTime, LocalDateTime fillTime) {
        if (filledQuantity != null) this.filledQuantity = filledQuantity;
        if (filledPrice != null) this.filledPrice = filledPrice;
        if (cumExecFee != null) this.cumExecFee = cumExecFee;
        if (realizedPnl != null) this.realizedPnl = realizedPnl;
        if (orderTime != null) this.orderTime = orderTime;
        if (fillTime != null) this.fillTime = fillTime;
    }
}