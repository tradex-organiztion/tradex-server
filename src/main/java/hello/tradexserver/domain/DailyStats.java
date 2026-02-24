package hello.tradexserver.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_stats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "stat_date"})
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyStats extends BaseTimeEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, name = "stat_date")
    private LocalDate statDate;

    private BigDecimal realizedPnl;        // 일일 실현 손익

    private int winCount;

    private int lossCount;

    private BigDecimal totalAsset;         // int → BigDecimal (정밀도)

    public BigDecimal getSafeRealizedPnl() {
        return realizedPnl != null ? realizedPnl : BigDecimal.ZERO;
    }

    public BigDecimal getSafeTotalAsset() {
        return totalAsset != null ? totalAsset : BigDecimal.ZERO;
    }

    public void accumulate(BigDecimal pnl) {
        this.realizedPnl = getSafeRealizedPnl().add(pnl);
        if (pnl.compareTo(BigDecimal.ZERO) > 0) this.winCount++;
        else if (pnl.compareTo(BigDecimal.ZERO) < 0) this.lossCount++;
    }

    public void updateTotalAsset(BigDecimal asset) {
        this.totalAsset = asset;
    }
}
