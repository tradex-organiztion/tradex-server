package hello.tradexserver.service;

import hello.tradexserver.domain.DailyStats;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.dto.response.portfolio.*;
import hello.tradexserver.openApi.rest.ExchangeFactory;
import hello.tradexserver.openApi.rest.ExchangeRestClient;
import hello.tradexserver.openApi.rest.dto.CoinBalanceDto;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;
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
public class PortfolioService {

    private final DailyStatsRepository dailyStatsRepository;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final ExchangeFactory exchangeFactory;

    /**
     * 포트폴리오 요약 조회
     * - 총 자산, 오늘의 손익, 주간 손익
     */
    public PortfolioSummaryResponse getPortfolioSummary(Long userId) {
        BigDecimal totalAsset = getTotalAsset(userId);
        LocalDate today = LocalDate.now();

        // 오늘의 손익
        BigDecimal todayPnl = BigDecimal.ZERO;
        BigDecimal todayPnlRate = BigDecimal.ZERO;
        Optional<DailyStats> todayStats = dailyStatsRepository.findByUserIdAndStatDate(userId, today);
        if (todayStats.isPresent() && todayStats.get().getRealizedPnl() != null) {
            todayPnl = todayStats.get().getRealizedPnl();
        }

        // 어제 총 자산으로 오늘 손익률 계산
        Optional<DailyStats> yesterdayStats = dailyStatsRepository.findByUserIdAndStatDate(userId, today.minusDays(1));
        if (yesterdayStats.isPresent() && yesterdayStats.get().getTotalAsset() != null
                && yesterdayStats.get().getTotalAsset().compareTo(BigDecimal.ZERO) > 0) {
            todayPnlRate = todayPnl
                    .divide(yesterdayStats.get().getTotalAsset(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // 주간 손익 (최근 7일)
        LocalDate weekAgo = today.minusDays(6);
        BigDecimal weeklyPnl = dailyStatsRepository.getWeeklyPnlSum(userId, weekAgo, today);
        if (weeklyPnl == null) {
            weeklyPnl = BigDecimal.ZERO;
        }

        // 주간 손익률 (7일 전 자산 기준)
        BigDecimal weeklyPnlRate = BigDecimal.ZERO;
        Optional<DailyStats> weekAgoStats = dailyStatsRepository.findByUserIdAndStatDate(userId, weekAgo);
        if (weekAgoStats.isPresent() && weekAgoStats.get().getTotalAsset() != null
                && weekAgoStats.get().getTotalAsset().compareTo(BigDecimal.ZERO) > 0) {
            weeklyPnlRate = weeklyPnl
                    .divide(weekAgoStats.get().getTotalAsset(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return PortfolioSummaryResponse.builder()
                .totalAsset(totalAsset.setScale(2, RoundingMode.HALF_UP))
                .todayPnl(todayPnl.setScale(2, RoundingMode.HALF_UP))
                .todayPnlRate(todayPnlRate.setScale(2, RoundingMode.HALF_UP))
                .weeklyPnl(weeklyPnl.setScale(2, RoundingMode.HALF_UP))
                .weeklyPnlRate(weeklyPnlRate.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * 누적 손익 시계열 조회
     */
    public CumulativeProfitResponse getCumulativeProfit(Long userId, String period,
                                                        LocalDate customStartDate, LocalDate customEndDate) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        // 기간 파싱
        if ("custom".equals(period) && customStartDate != null && customEndDate != null) {
            startDate = customStartDate;
            endDate = customEndDate;
        } else {
            int days = switch (period) {
                case "7d" -> 7;
                case "30d" -> 30;
                case "60d" -> 60;
                case "90d" -> 90;
                case "180d" -> 180;
                default -> 7;
            };
            startDate = endDate.minusDays(days - 1);
        }

        List<DailyStats> stats = dailyStatsRepository.findByUserIdAndStatDateBetween(userId, startDate, endDate);

        // 시작일 자산 (수익률 계산 기준)
        BigDecimal startAsset = BigDecimal.ZERO;
        Optional<DailyStats> startStats = dailyStatsRepository.findByUserIdAndStatDate(userId, startDate.minusDays(1));
        if (startStats.isPresent() && startStats.get().getTotalAsset() != null) {
            startAsset = startStats.get().getTotalAsset();
        }

        // 일별 데이터 맵
        Map<LocalDate, BigDecimal> pnlMap = stats.stream()
                .collect(Collectors.toMap(
                        DailyStats::getStatDate,
                        d -> d.getRealizedPnl() != null ? d.getRealizedPnl() : BigDecimal.ZERO
                ));

        List<CumulativeProfitResponse.DailyProfit> dailyProfits = new ArrayList<>();
        BigDecimal cumulativeProfit = BigDecimal.ZERO;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            BigDecimal dailyPnl = pnlMap.getOrDefault(date, BigDecimal.ZERO);
            cumulativeProfit = cumulativeProfit.add(dailyPnl);

            BigDecimal cumulativeRate = BigDecimal.ZERO;
            if (startAsset.compareTo(BigDecimal.ZERO) > 0) {
                cumulativeRate = cumulativeProfit
                        .divide(startAsset, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            dailyProfits.add(CumulativeProfitResponse.DailyProfit.builder()
                    .date(date)
                    .profit(dailyPnl.setScale(2, RoundingMode.HALF_UP))
                    .cumulativeProfit(cumulativeProfit.setScale(2, RoundingMode.HALF_UP))
                    .cumulativeProfitRate(cumulativeRate.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        BigDecimal totalProfitRate = BigDecimal.ZERO;
        if (startAsset.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitRate = cumulativeProfit
                    .divide(startAsset, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return CumulativeProfitResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalProfit(cumulativeProfit.setScale(2, RoundingMode.HALF_UP))
                .totalProfitRate(totalProfitRate.setScale(2, RoundingMode.HALF_UP))
                .dailyProfits(dailyProfits)
                .build();
    }

    /**
     * 월간 자산 추이 조회
     */
    public AssetHistoryResponse getAssetHistory(Long userId, int year, int month) {
        List<DailyStats> stats = dailyStatsRepository.findByUserIdAndYearMonth(userId, year, month);

        if (stats.isEmpty()) {
            return AssetHistoryResponse.builder()
                    .year(year)
                    .month(month)
                    .startAsset(BigDecimal.ZERO)
                    .endAsset(BigDecimal.ZERO)
                    .monthlyReturnRate(BigDecimal.ZERO)
                    .dailyAssets(List.of())
                    .build();
        }

        List<AssetHistoryResponse.DailyAsset> dailyAssets = new ArrayList<>();
        BigDecimal previousAsset = null;

        for (DailyStats stat : stats) {
            BigDecimal currentAsset = stat.getTotalAsset() != null ? stat.getTotalAsset() : BigDecimal.ZERO;
            BigDecimal dailyReturnRate = BigDecimal.ZERO;

            if (previousAsset != null && previousAsset.compareTo(BigDecimal.ZERO) > 0) {
                dailyReturnRate = currentAsset.subtract(previousAsset)
                        .divide(previousAsset, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            dailyAssets.add(AssetHistoryResponse.DailyAsset.builder()
                    .date(stat.getStatDate())
                    .totalAsset(currentAsset.setScale(2, RoundingMode.HALF_UP))
                    .dailyReturnRate(dailyReturnRate.setScale(2, RoundingMode.HALF_UP))
                    .build());

            previousAsset = currentAsset;
        }

        BigDecimal startAsset = stats.get(0).getTotalAsset() != null ? stats.get(0).getTotalAsset() : BigDecimal.ZERO;
        BigDecimal endAsset = stats.get(stats.size() - 1).getTotalAsset() != null
                ? stats.get(stats.size() - 1).getTotalAsset() : BigDecimal.ZERO;

        BigDecimal monthlyReturnRate = BigDecimal.ZERO;
        if (startAsset.compareTo(BigDecimal.ZERO) > 0) {
            monthlyReturnRate = endAsset.subtract(startAsset)
                    .divide(startAsset, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return AssetHistoryResponse.builder()
                .year(year)
                .month(month)
                .startAsset(startAsset.setScale(2, RoundingMode.HALF_UP))
                .endAsset(endAsset.setScale(2, RoundingMode.HALF_UP))
                .monthlyReturnRate(monthlyReturnRate.setScale(2, RoundingMode.HALF_UP))
                .dailyAssets(dailyAssets)
                .build();
    }

    /**
     * 월간 일별 손익 조회 (캘린더용)
     */
    public DailyProfitResponse getDailyProfit(Long userId, int year, int month) {
        List<DailyStats> stats = dailyStatsRepository.findByUserIdAndYearMonth(userId, year, month);

        BigDecimal monthlyTotalPnl = BigDecimal.ZERO;
        int totalWinCount = 0;
        int totalLossCount = 0;

        List<DailyProfitResponse.DailyPnl> dailyPnlList = new ArrayList<>();

        for (DailyStats stat : stats) {
            BigDecimal pnl = stat.getRealizedPnl() != null ? stat.getRealizedPnl() : BigDecimal.ZERO;
            monthlyTotalPnl = monthlyTotalPnl.add(pnl);
            totalWinCount += stat.getWinCount();
            totalLossCount += stat.getLossCount();

            dailyPnlList.add(DailyProfitResponse.DailyPnl.builder()
                    .date(stat.getStatDate())
                    .pnl(pnl.setScale(2, RoundingMode.HALF_UP))
                    .winCount(stat.getWinCount())
                    .lossCount(stat.getLossCount())
                    .build());
        }

        // 월초 자산 기준 수익률
        BigDecimal monthlyReturnRate = BigDecimal.ZERO;
        Optional<DailyStats> firstOfMonth = dailyStatsRepository.findFirstOfMonth(userId, year, month);
        if (firstOfMonth.isPresent() && firstOfMonth.get().getTotalAsset() != null
                && firstOfMonth.get().getTotalAsset().compareTo(BigDecimal.ZERO) > 0) {
            monthlyReturnRate = monthlyTotalPnl
                    .divide(firstOfMonth.get().getTotalAsset(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return DailyProfitResponse.builder()
                .year(year)
                .month(month)
                .monthlyTotalPnl(monthlyTotalPnl.setScale(2, RoundingMode.HALF_UP))
                .monthlyReturnRate(monthlyReturnRate.setScale(2, RoundingMode.HALF_UP))
                .totalWinCount(totalWinCount)
                .totalLossCount(totalLossCount)
                .dailyPnlList(dailyPnlList)
                .build();
    }

    /**
     * 자산 분포 조회
     */
    public AssetDistributionResponse getAssetDistribution(Long userId) {
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findActiveByUserId(userId);

        List<CoinBalanceDto> allCoins = new ArrayList<>();
        BigDecimal totalNetAsset = BigDecimal.ZERO;

        for (ExchangeApiKey key : apiKeys) {
            try {
                ExchangeRestClient client = exchangeFactory.getExchangeService(
                        key.getExchangeName(), key.getApiKey(), key.getApiSecret()
                );
                WalletBalanceResponse walletBalance = client.getWalletBalance();

                if (walletBalance != null) {
                    if (walletBalance.getTotalEquity() != null) {
                        totalNetAsset = totalNetAsset.add(walletBalance.getTotalEquity());
                    }
                    if (walletBalance.getCoins() != null) {
                        allCoins.addAll(walletBalance.getCoins());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get wallet balance from {}: {}", key.getExchangeName(), e.getMessage());
            }
        }

        // 동일 코인 합산
        Map<String, CoinBalanceDto> coinMap = new HashMap<>();
        for (CoinBalanceDto coin : allCoins) {
            coinMap.merge(coin.getCoin(), coin, (existing, newCoin) ->
                    CoinBalanceDto.builder()
                            .coin(existing.getCoin())
                            .walletBalance(existing.getWalletBalance().add(newCoin.getWalletBalance()))
                            .usdValue(existing.getUsdValue().add(newCoin.getUsdValue()))
                            .build()
            );
        }

        // 비중 계산 및 정렬
        final BigDecimal finalTotal = totalNetAsset;
        List<AssetDistributionResponse.CoinBalance> coinBalances = coinMap.values().stream()
                .map(coin -> {
                    BigDecimal percentage = BigDecimal.ZERO;
                    if (finalTotal.compareTo(BigDecimal.ZERO) > 0) {
                        percentage = coin.getUsdValue()
                                .divide(finalTotal, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                    }
                    return AssetDistributionResponse.CoinBalance.builder()
                            .coin(coin.getCoin())
                            .quantity(coin.getWalletBalance())
                            .usdValue(coin.getUsdValue().setScale(2, RoundingMode.HALF_UP))
                            .percentage(percentage.setScale(2, RoundingMode.HALF_UP))
                            .build();
                })
                .sorted((a, b) -> b.getUsdValue().compareTo(a.getUsdValue()))
                .collect(Collectors.toList());

        return AssetDistributionResponse.builder()
                .totalNetAsset(totalNetAsset.setScale(2, RoundingMode.HALF_UP))
                .coins(coinBalances)
                .build();
    }

    private BigDecimal getTotalAsset(Long userId) {
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findActiveByUserId(userId);

        return apiKeys.stream()
                .map(key -> {
                    try {
                        ExchangeRestClient client = exchangeFactory.getExchangeService(
                                key.getExchangeName(), key.getApiKey(), key.getApiSecret()
                        );
                        BigDecimal asset = client.getAsset();
                        return asset != null ? asset : BigDecimal.ZERO;
                    } catch (Exception e) {
                        log.warn("Failed to get asset from {}: {}", key.getExchangeName(), e.getMessage());
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
