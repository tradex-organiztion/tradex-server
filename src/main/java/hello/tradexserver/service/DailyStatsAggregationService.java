package hello.tradexserver.service;

import hello.tradexserver.domain.DailyStats;
import hello.tradexserver.domain.User;
import hello.tradexserver.repository.DailyStatsRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DailyStatsAggregationService {

    private final DailyStatsRepository dailyStatsRepository;
    private final UserRepository userRepository;
    private final ExchangeAssetService exchangeAssetService;

    /**
     * 포지션 청산 이벤트 → 오늘 실현 손익/승패 누적
     */
    public void accumulatePnl(Long userId, BigDecimal pnl) {
        DailyStats stats = findOrCreate(userId, LocalDate.now());
        stats.accumulate(pnl);
    }

    /**
     * 홈 화면 접속 시 → 오늘 totalAsset lazy 저장.
     * 오늘 이미 저장된 값이 있으면 외부 API 호출 스킵.
     */
    public BigDecimal upsertTodayTotalAsset(Long userId) {
        DailyStats stats = findOrCreate(userId, LocalDate.now());
        if (stats.getTotalAsset() == null) {
            BigDecimal asset = exchangeAssetService.getTotalAsset(userId);
            stats.updateTotalAsset(asset);
            return asset;
        }
        return stats.getTotalAsset();
    }

    /**
     * 자정 배치 → 어제 totalAsset 스냅샷 (항상 최신으로 덮어쓰기)
     */
    public void snapshotYesterdayTotalAsset(Long userId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        DailyStats stats = findOrCreate(userId, yesterday);
        BigDecimal asset = exchangeAssetService.getTotalAsset(userId);
        stats.updateTotalAsset(asset);
    }

    private DailyStats findOrCreate(Long userId, LocalDate date) {
        return dailyStatsRepository.findByUserIdAndStatDate(userId, date)
                .orElseGet(() -> {
                    User user = userRepository.getReferenceById(userId);
                    return dailyStatsRepository.save(DailyStats.builder()
                            .user(user)
                            .statDate(date)
                            .realizedPnl(BigDecimal.ZERO)
                            .winCount(0)
                            .lossCount(0)
                            .build());
                });
    }
}
