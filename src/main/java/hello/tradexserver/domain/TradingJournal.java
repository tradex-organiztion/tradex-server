package hello.tradexserver.domain;

import com.pgvector.PGvector;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "trading_journals")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingJournal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", unique = true, nullable = false)
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(precision = 20, scale = 8)
    private BigDecimal plannedTargetPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal plannedStopLoss;

    @Column(columnDefinition = "TEXT")
    private String entryScenario;

    @Column(columnDefinition = "TEXT")
    private String exitReview;

    //@Column(columnDefinition = "vector(1536)")
    //private PGvector originalEmbedding;

    @OneToOne(mappedBy = "tradingJournal", fetch = FetchType.LAZY)
    private RefinedJournal refinedJournal;
}