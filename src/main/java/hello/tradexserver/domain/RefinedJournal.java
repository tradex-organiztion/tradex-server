package hello.tradexserver.domain;

import com.pgvector.PGvector;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "refined_journals")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefinedJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id", unique = true, nullable = false)
    private TradingJournal tradingJournal;

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

    @Builder.Default
    private Boolean isEmotionalTrade = false;

    @Builder.Default
    private Boolean isUnplannedEntry = false;

    @Column(columnDefinition = "TEXT")
    private String refinedText;

    //@Column(columnDefinition = "vector(1536)")
    //private PGvector refinedEmbedding;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}