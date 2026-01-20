package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "strategy_patterns",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "user_id", "indicators", "technical_analysis", "timeframes",
                "trading_style", "position_side", "market_condition"
        }))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> indicators;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> technicalAnalysis;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> timeframes;

    @Column(length = 20)
    private String tradingStyle;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PositionSide positionSide;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MarketCondition marketCondition;

    @Builder.Default
    private Integer totalTrades = 0;

    @Builder.Default
    private Integer winCount = 0;

    @Builder.Default
    private Integer lossCount = 0;

    @Column(precision = 5, scale = 2)
    private BigDecimal winRate;

    @Column(precision = 20, scale = 8)
    private BigDecimal avgProfit;

    @Column(precision = 10, scale = 2)
    private BigDecimal avgRrRatio;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}