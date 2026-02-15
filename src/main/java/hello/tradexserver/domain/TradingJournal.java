package hello.tradexserver.domain;

import com.pgvector.PGvector;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    @ElementCollection
    @CollectionTable(name = "journal_indicators", joinColumns = @JoinColumn(name = "journal_id"))
    @Column(name = "indicator")
    @Builder.Default
    private List<String> indicators = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "journal_timeframes", joinColumns = @JoinColumn(name = "journal_id"))
    @Column(name = "timeframe")
    @Builder.Default
    private List<String> timeframes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "journal_technical_analyses", joinColumns = @JoinColumn(name = "journal_id"))
    @Column(name = "technical_analysis", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> technicalAnalyses = new ArrayList<>();

    //@Column(columnDefinition = "vector(1536)")
    //private PGvector originalEmbedding;

    @OneToOne(mappedBy = "tradingJournal", fetch = FetchType.LAZY)
    private RefinedJournal refinedJournal;

    public void update(BigDecimal plannedTargetPrice, BigDecimal plannedStopLoss,
                       String entryScenario, String exitReview,
                       List<String> indicators, List<String> timeframes, List<String> technicalAnalyses) {
        if (plannedTargetPrice != null) this.plannedTargetPrice = plannedTargetPrice;
        if (plannedStopLoss != null) this.plannedStopLoss = plannedStopLoss;
        if (entryScenario != null) this.entryScenario = entryScenario;
        if (exitReview != null) this.exitReview = exitReview;
        if (indicators != null) this.indicators = indicators;
        if (timeframes != null) this.timeframes = timeframes;
        if (technicalAnalyses != null) this.technicalAnalyses = technicalAnalyses;
    }
}