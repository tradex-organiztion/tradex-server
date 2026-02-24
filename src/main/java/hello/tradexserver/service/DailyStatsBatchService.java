package hello.tradexserver.service;

import hello.tradexserver.repository.DailyStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStatsBatchService {

    private final DailyStatsRepository dailyStatsRepository;
    private final DailyStatsAggregationService aggregationService;

    /**
     * 자정 배치: 최근 7일 내 활성 유저의 어제 totalAsset 스냅샷 저장.
     * 어제 접속하지 않아 레코드가 없는 유저도 포함하여 "어제 대비" 비교가 가능하도록 함.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void snapshotTotalAsset() {
        LocalDate since = LocalDate.now().minusDays(7);
        List<Long> activeUserIds = dailyStatsRepository.findActiveUserIds(since);

        log.info("[DailyStatsBatch] totalAsset 스냅샷 시작 - 활성 유저: {}명", activeUserIds.size());

        int success = 0;
        int fail = 0;
        for (Long userId : activeUserIds) {
            try {
                aggregationService.snapshotYesterdayTotalAsset(userId);
                success++;
            } catch (Exception e) {
                log.warn("[DailyStatsBatch] userId={} totalAsset 스냅샷 실패: {}", userId, e.getMessage());
                fail++;
            }
        }

        log.info("[DailyStatsBatch] totalAsset 스냅샷 완료 - 성공: {}, 실패: {}", success, fail);
    }
}
