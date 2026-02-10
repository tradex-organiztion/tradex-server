package hello.tradexserver.service;

import hello.tradexserver.domain.DailyStats;
import hello.tradexserver.dto.response.portfolio.*;
import hello.tradexserver.openApi.rest.dto.CoinBalanceDto;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;
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
public class PortfolioService {

    private final DailyStatsRepository dailyStatsRepository;
    private final ExchangeAssetService exchangeAssetService;

    /**
     * 포트폴리오 요약 조회
     * - 총 자산, 오늘의 손익, 주간 손익
     */
    public PortfolioSummaryResponse getPortfolioSummary(Long userId) {
        BigDecimal totalAsset = exchangeAssetService.getTotalAsset(userId);
        LocalDate today = LocalDate.now();

        // 오늘의 손익
        BigDecimal todayPnl = dailyStatsRepository.findByUserIdAndStatDate(userId, today)
                .map(DailyStats::getSafeRealizedPnl)
                .orElse(BigDecimal.ZERO);

        // 어제 총 자산으로 오늘 손익률 계산
        BigDecimal yesterdayAsset = dailyStatsRepository.findByUserIdAndStatDate(userId, today.minusDays(1))
                .map(DailyStats::getSafeTotalAsset)
                .orElse(BigDecimal.ZERO);
        BigDecimal todayPnlRate = calcRate(todayPnl, yesterdayAsset);

        // 주간 손익 (최근 7일)
        LocalDate weekAgo = today.minusDays(6);
        BigDecimal weeklyPnl = nullToZero(dailyStatsRepository.getWeeklyPnlSum(userId, weekAgo, today));

        // 주간 손익률 (7일 전 자산 기준)
        BigDecimal weekAgoAsset = dailyStatsRepository.findByUserIdAndStatDate(userId, weekAgo)
                .map(DailyStats::getSafeTotalAsset)
                .orElse(BigDecimal.ZERO);
        BigDecimal weeklyPnlRate = calcRate(weeklyPnl, weekAgoAsset);

        return PortfolioSummaryResponse.builder()
                .totalAsset(scale2(totalAsset))
                .todayPnl(scale2(todayPnl))
                .todayPnlRate(scale2(todayPnlRate))
                .weeklyPnl(scale2(weeklyPnl))
                .weeklyPnlRate(scale2(weeklyPnlRate))
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
        BigDecimal startAsset = dailyStatsRepository.findByUserIdAndStatDate(userId, startDate.minusDays(1))
                .map(DailyStats::getSafeTotalAsset)
                .orElse(BigDecimal.ZERO);

        // 일별 데이터 맵
        Map<LocalDate, BigDecimal> pnlMap = stats.stream()
                .collect(Collectors.toMap(
                        DailyStats::getStatDate,
                        DailyStats::getSafeRealizedPnl
                ));

        List<CumulativeProfitResponse.DailyProfit> dailyProfits = new ArrayList<>();
        BigDecimal cumulativeProfit = BigDecimal.ZERO;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            BigDecimal dailyPnl = pnlMap.getOrDefault(date, BigDecimal.ZERO);
            cumulativeProfit = cumulativeProfit.add(dailyPnl);

            dailyProfits.add(CumulativeProfitResponse.DailyProfit.builder()
                    .date(date)
                    .profit(scale2(dailyPnl))
                    .cumulativeProfit(scale2(cumulativeProfit))
                    .cumulativeProfitRate(scale2(calcRate(cumulativeProfit, startAsset)))
                    .build());
        }

        return CumulativeProfitResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalProfit(scale2(cumulativeProfit))
                .totalProfitRate(scale2(calcRate(cumulativeProfit, startAsset)))
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
            BigDecimal currentAsset = stat.getSafeTotalAsset();
            BigDecimal dailyReturnRate = previousAsset != null
                    ? calcRate(currentAsset.subtract(previousAsset), previousAsset)
                    : BigDecimal.ZERO;

            dailyAssets.add(AssetHistoryResponse.DailyAsset.builder()
                    .date(stat.getStatDate())
                    .totalAsset(scale2(currentAsset))
                    .dailyReturnRate(scale2(dailyReturnRate))
                    .build());

            previousAsset = currentAsset;
        }

        BigDecimal startAsset = stats.get(0).getSafeTotalAsset();
        BigDecimal endAsset = stats.get(stats.size() - 1).getSafeTotalAsset();
        BigDecimal monthlyReturnRate = calcRate(endAsset.subtract(startAsset), startAsset);

        return AssetHistoryResponse.builder()
                .year(year)
                .month(month)
                .startAsset(scale2(startAsset))
                .endAsset(scale2(endAsset))
                .monthlyReturnRate(scale2(monthlyReturnRate))
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
            BigDecimal pnl = stat.getSafeRealizedPnl();
            monthlyTotalPnl = monthlyTotalPnl.add(pnl);
            totalWinCount += stat.getWinCount();
            totalLossCount += stat.getLossCount();

            dailyPnlList.add(DailyProfitResponse.DailyPnl.builder()
                    .date(stat.getStatDate())
                    .pnl(scale2(pnl))
                    .winCount(stat.getWinCount())
                    .lossCount(stat.getLossCount())
                    .build());
        }

        // 월초 자산 기준 수익률
        BigDecimal firstOfMonthAsset = dailyStatsRepository.findFirstOfMonth(userId, year, month)
                .map(DailyStats::getSafeTotalAsset)
                .orElse(BigDecimal.ZERO);
        BigDecimal monthlyReturnRate = calcRate(monthlyTotalPnl, firstOfMonthAsset);

        return DailyProfitResponse.builder()
                .year(year)
                .month(month)
                .monthlyTotalPnl(scale2(monthlyTotalPnl))
                .monthlyReturnRate(scale2(monthlyReturnRate))
                .totalWinCount(totalWinCount)
                .totalLossCount(totalLossCount)
                .dailyPnlList(dailyPnlList)
                .build();
    }

    /**
     * 자산 분포 조회
     */
    public AssetDistributionResponse getAssetDistribution(Long userId) {
        WalletBalanceResponse walletBalance = exchangeAssetService.getAggregatedWalletBalance(userId);

        BigDecimal totalNetAsset = nullToZero(walletBalance.getTotalEquity());
        List<CoinBalanceDto> allCoins = walletBalance.getCoins() != null ? walletBalance.getCoins() : List.of();

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
                .map(coin -> AssetDistributionResponse.CoinBalance.builder()
                        .coin(coin.getCoin())
                        .quantity(coin.getWalletBalance())
                        .usdValue(scale2(coin.getUsdValue()))
                        .percentage(scale2(calcRate(coin.getUsdValue(), finalTotal)))
                        .build())
                .sorted((a, b) -> b.getUsdValue().compareTo(a.getUsdValue()))
                .collect(Collectors.toList());

        return AssetDistributionResponse.builder()
                .totalNetAsset(scale2(totalNetAsset))
                .coins(coinBalances)
                .build();
    }
}
