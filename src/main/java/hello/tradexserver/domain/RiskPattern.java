package hello.tradexserver.domain;

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
@Table(name = "risk_patterns",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long riskId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    private Integer unplannedEntryCount = 0;

    @Builder.Default
    private Integer emotionalTradeCount = 0;

    @Builder.Default
    private Integer consecutiveEntryCount = 0;

    @Builder.Default
    private Integer slViolationCount = 0;

    @Builder.Default
    private Integer earlyTpCount = 0;

    @Builder.Default
    private Integer averagingDownCount = 0;

    @Builder.Default
    private Integer totalTrades = 0;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> hourlyWinRates;

    @Column(precision = 5, scale = 2)
    private BigDecimal uptrendWinRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal downtrendWinRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal sidewaysWinRate;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}