package hello.tradexserver.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "trading_principle_checks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingPrincipleCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_journal_id", nullable = false)
    private TradingJournal tradingJournal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_principle_id", nullable = false)
    private TradingPrinciple tradingPrinciple;

    @Column(nullable = false)
    private boolean isChecked;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}