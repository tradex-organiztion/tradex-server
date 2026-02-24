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

    // ============ 사전 시나리오 ============

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

    @Column(precision = 20, scale = 8)
    private BigDecimal targetPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(columnDefinition = "TEXT")
    private String entryReason;

    @Column(columnDefinition = "TEXT")
    private String targetScenario;

    // ============ 매매 후 복기 ============

    @Column(columnDefinition = "TEXT")
    private String chartScreenshotUrl;

    @Column(columnDefinition = "TEXT")
    private String reviewContent;

    // ============ 매매원칙 준수 체크 ============

    @OneToMany(mappedBy = "tradingJournal", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TradingPrincipleCheck> principleChecks = new ArrayList<>();

    @OneToOne(mappedBy = "tradingJournal", fetch = FetchType.LAZY)
    private RefinedJournal refinedJournal;

    public void update(BigDecimal targetPrice, BigDecimal stopLoss,
                       String entryReason, String targetScenario,
                       String chartScreenshotUrl, String reviewContent,
                       List<String> indicators, List<String> timeframes, List<String> technicalAnalyses) {
        if (targetPrice != null) this.targetPrice = targetPrice;
        if (stopLoss != null) this.stopLoss = stopLoss;
        if (entryReason != null) this.entryReason = entryReason;
        if (targetScenario != null) this.targetScenario = targetScenario;
        if (chartScreenshotUrl != null) this.chartScreenshotUrl = chartScreenshotUrl;
        if (reviewContent != null) this.reviewContent = reviewContent;
        if (indicators != null) this.indicators = indicators;
        if (timeframes != null) this.timeframes = timeframes;
        if (technicalAnalyses != null) this.technicalAnalyses = technicalAnalyses;
    }
}