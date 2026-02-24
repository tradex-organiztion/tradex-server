package hello.tradexserver.service;

import hello.tradexserver.domain.DailyStats;
import hello.tradexserver.dto.response.*;
import hello.tradexserver.repository.DailyStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static hello.tradexserver.common.util.BigDecimalUtil.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyStatsService {

    private final DailyStatsRepository dailyStatsRepository;
    private final DailyStatsAggregationService aggregationService;

    public HomeScreenResponse getHomeScreenData(Long userId) {
        return HomeScreenResponse.of(
                getAsset(userId),
                getMonthlyPnl(userId),
                getWinRateLast7Days(userId),
                getDailyPnlChart(userId)
        );
    }

    /**
     * 1. 총 자산 + 전일 대비 증감률(%)
     * 오늘 totalAsset이 없으면 외부 API 호출 후 DB에 저장 (lazy snapshot).
     */
    private AssetData getAsset(Long userId) {
        BigDecimal currentAsset = aggregationService.upsertTodayTotalAsset(userId);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        BigDecimal yesterdayAsset = dailyStatsRepository
                .findByUserIdAndStatDate(userId, yesterday)
                .map(DailyStats::getSafeTotalAsset)
                .orElse(BigDecimal.ZERO);

        BigDecimal changeRate = calcRate(currentAsset.subtract(yesterdayAsset), yesterdayAsset);

        return AssetData.of(currentAsset, yesterdayAsset, changeRate);
    }

    /**
     * 2. 이번 달 수익 + 지난달 수익 + 달성률(%)
     */
    private MonthlyPnlData getMonthlyPnl(Long userId) {
        LocalDate today = LocalDate.now();

        // 이번 달 범위: 1월 1일 ~ 오늘
        LocalDate thisMonthStart = today.withDayOfMonth(1);
        // 지난달 범위: 12월 1일 ~ 12월 31일
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        LocalDate lastMonthEnd = thisMonthStart.minusDays(1);

        // 이번 달 누적
        List<DailyStats> thisMonthStats = dailyStatsRepository
                .findByUserIdAndStatDateBetween(userId, thisMonthStart, today);
        BigDecimal thisMonthPnl = thisMonthStats.stream()
                .map(DailyStats::getSafeRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 지난달 누적
        List<DailyStats> lastMonthStats = dailyStatsRepository
                .findByUserIdAndStatDateBetween(userId, lastMonthStart, lastMonthEnd);
        BigDecimal lastMonthPnl = lastMonthStats.stream()
                .map(DailyStats::getSafeRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 달성률(%)
        BigDecimal achievementRate = calcRate(thisMonthPnl, lastMonthPnl);

        return MonthlyPnlData.of(thisMonthPnl, lastMonthPnl, achievementRate);
    }

    /**
     * 3. 최근 7일 승패 수 + 승률(%)
     */
    private WinRateData getWinRateLast7Days(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        List<DailyStats> last7Days = dailyStatsRepository
                .findByUserIdAndStatDateBetween(userId, sevenDaysAgo, today);

        int totalWins = last7Days.stream()
                .mapToInt(DailyStats::getWinCount)
                .sum();
        int totalLosses = last7Days.stream()
                .mapToInt(DailyStats::getLossCount)
                .sum();

        int totalTrades = totalWins + totalLosses;
        BigDecimal winRate = BigDecimal.ZERO;
        if (totalTrades > 0) {
            winRate = calcRate(BigDecimal.valueOf(totalWins), BigDecimal.valueOf(totalTrades));
        }

        return WinRateData.of(totalWins, totalLosses, winRate);
    }

    /**
     * 4. 최근 7일 PnL 누적 곡선
     * D-6을 기준점(0)으로 설정, 각 날짜의 realizedPnl 누적 합산
     */
    private List<DailyPnlChartData> getDailyPnlChart(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        List<DailyStats> last7Days = dailyStatsRepository
                .findByUserIdAndStatDateBetween(userId, sevenDaysAgo, today);

        Map<LocalDate, BigDecimal> pnlMap = last7Days.stream()
                .collect(Collectors.toMap(
                        DailyStats::getStatDate,
                        DailyStats::getSafeRealizedPnl
                ));

        List<DailyPnlChartData> result = new ArrayList<>();
        BigDecimal cumulativePnl = BigDecimal.ZERO;

        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = sevenDaysAgo.plusDays(i);
            BigDecimal dailyPnl = pnlMap.getOrDefault(currentDate, BigDecimal.ZERO);
            cumulativePnl = cumulativePnl.add(dailyPnl);

            result.add(DailyPnlChartData.of(currentDate, cumulativePnl));
        }

        return result;
    }
}
