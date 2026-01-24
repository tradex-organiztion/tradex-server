package hello.tradexserver.service;

import hello.tradexserver.domain.DailyStats;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.dto.response.*;
import hello.tradexserver.openApi.rest.ExchangeFactory;
import hello.tradexserver.openApi.rest.ExchangeRestClient;
import hello.tradexserver.repository.DailyStatsRepository;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyStatsService {

    private final DailyStatsRepository dailyStatsRepository;
    private final ExchangeFactory exchangeFactory;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;

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
     */
    private AssetData getAsset(Long userId) {
        BigDecimal currentAsset = getTotalAsset(userId);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        Optional<DailyStats> yesterdayData = dailyStatsRepository
                .findByUserIdAndStatDate(userId, yesterday);

        BigDecimal changeRate = BigDecimal.ZERO;
        BigDecimal yesterdayAsset = BigDecimal.ZERO;

        // 전일 대비 증감률 계산
        if (yesterdayData.isPresent()) {
            yesterdayAsset = yesterdayData.get().getTotalAsset();
            // 어제 자산이 0이 아니어야 계산
            if (yesterdayAsset.compareTo(BigDecimal.ZERO) > 0) {
                // (현재 - 어제) / 어제 * 100
                changeRate = currentAsset.subtract(yesterdayAsset)
                        .divide(yesterdayAsset, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
        }

        return AssetData.of(currentAsset, yesterdayAsset, changeRate);
    }

    private BigDecimal getTotalAsset(Long userId) {
        // 사용자의 활성화된 모든 거래소 API 키 조회
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findActiveByUserId(userId);

        // 각 거래소별로 잔고 조회 후 합산
        return apiKeys.stream()
                .map(key -> {
                    try {
                        ExchangeRestClient client = exchangeFactory.getExchangeService(
                                key.getExchangeName(), key.getApiKey(), key.getApiSecret()
                        );
                        BigDecimal asset = client.getAsset();
                        return asset != null ? asset : BigDecimal.ZERO;
                    } catch (Exception e) {
                        // API 호출 실패 시 로깅 후 0 반환
                        log.warn("Failed to get asset from {}: {}", key.getExchangeName(), e.getMessage());
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                .map(DailyStats::getRealizedPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 지난달 누적
        List<DailyStats> lastMonthStats = dailyStatsRepository
                .findByUserIdAndStatDateBetween(userId, lastMonthStart, lastMonthEnd);
        BigDecimal lastMonthPnl = lastMonthStats.stream()
                .map(DailyStats::getRealizedPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 달성률(%)
        // 지난달이 0이면 달성률도 0 (0으로 나누기 방지)
        BigDecimal achievementRate = BigDecimal.ZERO;
        if (lastMonthPnl.compareTo(BigDecimal.ZERO) > 0) {
            // 이번 달 / 지난달 * 100
            achievementRate = thisMonthPnl.divide(lastMonthPnl, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return MonthlyPnlData.of(thisMonthPnl, lastMonthPnl, achievementRate);
    }

    /**
     * 3. 최근 7일 승패 수 + 승률(%)
     */
    private WinRateData getWinRateLast7Days(Long userId) {
        LocalDate today = LocalDate.now();
        // 최근 7일 범위: 오늘로부터 6일 전 ~ 오늘
        LocalDate sevenDaysAgo = today.minusDays(6);

        List<DailyStats> last7Days = dailyStatsRepository
                .findByUserIdAndStatDateBetween(userId, sevenDaysAgo, today);
        // 최대 7개 데이터 조회 (없는 날은 조회 안 됨)

        // **7일 승패 수 합산**
        int totalWins = last7Days.stream()
                .mapToInt(DailyStats::getWinCount)
                .sum();
        int totalLosses = last7Days.stream()
                .mapToInt(DailyStats::getLossCount)
                .sum();

        // **승률(%) 계산**
        int totalTrades = totalWins + totalLosses;
        BigDecimal winRate = BigDecimal.ZERO;
        if (totalTrades > 0) {
            winRate = BigDecimal.valueOf(totalWins)
                    .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
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
                        d -> d.getRealizedPnl() != null ? d.getRealizedPnl() : BigDecimal.ZERO
                ));

        List<DailyPnlChartData> result = new ArrayList<>();
        BigDecimal cumulativePnl = BigDecimal.ZERO;

        // D-6부터 D-Day까지 7개 포인트 생성
        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = sevenDaysAgo.plusDays(i);
            // 해당 날짜의 일일 손익 (없으면 0)
            BigDecimal dailyPnl = pnlMap.getOrDefault(currentDate, BigDecimal.ZERO);
            // 누적값에 더하기
            cumulativePnl = cumulativePnl.add(dailyPnl);

            result.add(DailyPnlChartData.of(currentDate, cumulativePnl));
        }

        return result;
    }
}
