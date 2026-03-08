package hello.tradexserver.service;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.dto.response.strategy.PerformanceResponse;
import hello.tradexserver.dto.response.strategy.StrategyAnalysisResponse;
import hello.tradexserver.dto.response.strategy.StrategyAnalysisResponse.StrategyItem;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrategyAnalysisService {

    private final PositionRepository positionRepository;

    public StrategyAnalysisResponse analyze(Long userId, String exchangeName, String period,
                                            LocalDate startDate, LocalDate endDate) {
        ExchangeName exchange = exchangeName != null ? ExchangeName.valueOf(exchangeName.toUpperCase()) : null;
        LocalDateTime[] range = resolveRange(period, startDate, endDate);

        // findClosedWithJournalForRiskAnalysis 재활용 (Position + TradingJournal JOIN FETCH, 단일 쿼리)
        List<Position> positions = positionRepository
                .findClosedWithJournalForRiskAnalysis(userId, exchange, range[0], range[1]);

        if (positions.isEmpty()) {
            return StrategyAnalysisResponse.builder()
                    .totalTrades(0)
                    .strategies(List.of())
                    .build();
        }

        // 매매일지가 있는 포지션만 집계 (일지 없으면 전략 키 구성 불가)
        Map<StrategyKey, List<Position>> grouped = positions.stream()
                .filter(p -> p.getTradingJournal() != null)
                .collect(Collectors.groupingBy(p -> buildKey(p, p.getTradingJournal())));

        List<StrategyItem> strategies = grouped.entrySet().stream()
                .map(e -> buildItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(StrategyItem::getWinRate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return StrategyAnalysisResponse.builder()
                .totalTrades(positions.size())
                .strategies(strategies)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 성과 곡선 (일별/주별/월별 PnL + 최대 연속 승/패)
    // ──────────────────────────────────────────────────────────────────────────

    public PerformanceResponse performance(Long userId, String exchangeName, String period,
                                           LocalDate startDate, LocalDate endDate) {
        ExchangeName exchange = exchangeName != null ? ExchangeName.valueOf(exchangeName.toUpperCase()) : null;
        LocalDateTime[] range = resolveRange(period, startDate, endDate);

        List<Position> positions = positionRepository.findClosedForPerformance(
                userId, exchange, range[0], range[1]);

        if (positions.isEmpty()) {
            return PerformanceResponse.builder()
                    .granularity("DAILY")
                    .data(List.of())
                    .summary(PerformanceResponse.Summary.builder()
                            .maxWinStreak(0).maxLossStreak(0).totalPnl(BigDecimal.ZERO).build())
                    .build();
        }

        String granularity = resolveGranularity(period, startDate, endDate, positions);

        return PerformanceResponse.builder()
                .granularity(granularity)
                .data(buildDataPoints(positions, granularity))
                .summary(buildSummary(positions))
                .build();
    }

    private String resolveGranularity(String period, LocalDate startDate, LocalDate endDate,
                                       List<Position> positions) {
        long days;
        if ("all".equals(period)) {
            LocalDate first = positions.get(0).getExitTime().toLocalDate();
            LocalDate last = positions.get(positions.size() - 1).getExitTime().toLocalDate();
            days = java.time.temporal.ChronoUnit.DAYS.between(first, last);
        } else if ("custom".equals(period)) {
            LocalDate s = startDate != null ? startDate : LocalDate.now().minusDays(30);
            LocalDate e = endDate != null ? endDate : LocalDate.now();
            days = java.time.temporal.ChronoUnit.DAYS.between(s, e);
        } else {
            return switch (period == null ? "30d" : period) {
                case "7d", "30d" -> "DAILY";
                default -> "WEEKLY"; // 60d, 90d, 180d
            };
        }
        if (days <= 30) return "DAILY";
        if (days <= 365) return "WEEKLY";
        return "MONTHLY";
    }

    private List<PerformanceResponse.DataPoint> buildDataPoints(List<Position> positions, String granularity) {
        // exitTime ASC로 이미 정렬된 positions → LinkedHashMap으로 순서 유지
        Map<String, List<Position>> grouped = new LinkedHashMap<>();
        for (Position p : positions) {
            String key = getPeriodKey(p.getExitTime().toLocalDate(), granularity);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        BigDecimal cumulative = BigDecimal.ZERO;
        List<PerformanceResponse.DataPoint> result = new ArrayList<>();

        for (Map.Entry<String, List<Position>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<Position> group = entry.getValue();

            BigDecimal pnl = group.stream()
                    .map(p -> p.getRealizedPnl() != null ? p.getRealizedPnl() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            cumulative = cumulative.add(pnl);

            int wins = (int) group.stream()
                    .filter(p -> p.getRealizedPnl() != null && p.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                    .count();

            LocalDate[] dateRange = getDateRange(key, granularity);

            result.add(PerformanceResponse.DataPoint.builder()
                    .label(toLabel(key, granularity))
                    .startDate(dateRange[0].toString())
                    .endDate(dateRange[1].toString())
                    .pnl(pnl.setScale(2, RoundingMode.HALF_UP))
                    .cumulativePnl(cumulative.setScale(2, RoundingMode.HALF_UP))
                    .tradeCount(group.size())
                    .winCount(wins)
                    .lossCount(group.size() - wins)
                    .build());
        }
        return result;
    }

    /** 포지션을 단위별 키로 변환 (DAILY: "2026-02-15", WEEKLY: "2026-07", MONTHLY: "2026-02") */
    private String getPeriodKey(LocalDate date, String granularity) {
        return switch (granularity) {
            case "WEEKLY" -> String.format("%d-%02d",
                    date.get(IsoFields.WEEK_BASED_YEAR),
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "MONTHLY" -> String.format("%d-%02d", date.getYear(), date.getMonthValue());
            default -> date.toString(); // DAILY
        };
    }

    /** 응답용 label (WEEKLY만 "2026-W07" 형식으로 변환, 나머지는 key 그대로) */
    private String toLabel(String key, String granularity) {
        if ("WEEKLY".equals(granularity)) {
            String[] parts = key.split("-");
            return parts[0] + "-W" + parts[1];
        }
        return key;
    }

    private LocalDate[] getDateRange(String key, String granularity) {
        return switch (granularity) {
            case "WEEKLY" -> {
                String[] parts = key.split("-");
                LocalDate monday = LocalDate.now()
                        .with(IsoFields.WEEK_BASED_YEAR, Long.parseLong(parts[0]))
                        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, Long.parseLong(parts[1]))
                        .with(DayOfWeek.MONDAY);
                yield new LocalDate[]{monday, monday.plusDays(6)};
            }
            case "MONTHLY" -> {
                String[] parts = key.split("-");
                LocalDate first = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
                yield new LocalDate[]{first, first.withDayOfMonth(first.lengthOfMonth())};
            }
            default -> { // DAILY
                LocalDate date = LocalDate.parse(key);
                yield new LocalDate[]{date, date};
            }
        };
    }

    private PerformanceResponse.Summary buildSummary(List<Position> positions) {
        int maxWin = 0, maxLoss = 0, curWin = 0, curLoss = 0;
        BigDecimal totalPnl = BigDecimal.ZERO;

        for (Position p : positions) {
            BigDecimal pnl = p.getRealizedPnl() != null ? p.getRealizedPnl() : BigDecimal.ZERO;
            totalPnl = totalPnl.add(pnl);
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                curWin++;
                curLoss = 0;
                maxWin = Math.max(maxWin, curWin);
            } else {
                curLoss++;
                curWin = 0;
                maxLoss = Math.max(maxLoss, curLoss);
            }
        }

        return PerformanceResponse.Summary.builder()
                .maxWinStreak(maxWin)
                .maxLossStreak(maxLoss)
                .totalPnl(totalPnl.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 전략 키 구성
    // ──────────────────────────────────────────────────────────────────────────

    private StrategyKey buildKey(Position p, TradingJournal j) {
        // 리스트 정렬: ["MACD","RSI"] == ["RSI","MACD"] 동일 전략으로 처리
        return new StrategyKey(
                sortedList(j.getIndicators()),
                sortedList(j.getTechnicalAnalyses()),
                sortedList(j.getTimeframes()),
                p.getSide(),
                p.getMarketCondition()
        );
    }

    private List<String> sortedList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().sorted().toList();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 전략 항목 집계
    // ──────────────────────────────────────────────────────────────────────────

    private StrategyItem buildItem(StrategyKey key, List<Position> group) {
        int total = group.size();
        int wins = 0;
        BigDecimal profitSum = BigDecimal.ZERO;
        List<BigDecimal> winPnls = new ArrayList<>();
        List<BigDecimal> lossPnls = new ArrayList<>();

        for (Position p : group) {
            BigDecimal pnl = p.getRealizedPnl() != null ? p.getRealizedPnl() : BigDecimal.ZERO;
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                winPnls.add(pnl);
            } else {
                lossPnls.add(pnl.abs());
            }
            profitSum = profitSum.add(pnl);
        }

        BigDecimal winRate = total > 0
                ? BigDecimal.valueOf(wins)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal avgProfit = total > 0
                ? profitSum.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return StrategyItem.builder()
                .indicators(key.indicators())
                .technicalAnalyses(key.technicalAnalyses())
                .timeframes(key.timeframes())
                .side(key.side())
                .marketCondition(key.marketCondition())
                .totalTrades(total)
                .winCount(wins)
                .lossCount(total - wins)
                .winRate(winRate)
                .avgProfit(avgProfit)
                .avgRrRatio(calcAvgRrRatio(winPnls, lossPnls))
                .build();
    }

    /** 평균 R/R = avgWin / avgLoss (실현 손익 기준) */
    private BigDecimal calcAvgRrRatio(List<BigDecimal> winPnls, List<BigDecimal> lossPnls) {
        if (winPnls.isEmpty() || lossPnls.isEmpty()) return null;
        BigDecimal avgWin = winPnls.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(winPnls.size()), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = lossPnls.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(lossPnls.size()), 8, RoundingMode.HALF_UP);
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return null;
        return avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 기간 변환 (RiskAnalysisService와 동일 규칙)
    // ──────────────────────────────────────────────────────────────────────────

    private LocalDateTime[] resolveRange(String period, LocalDate startDate, LocalDate endDate) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period == null ? "30d" : period) {
            case "7d"    -> new LocalDateTime[]{ now.minusDays(7), null };
            case "30d"   -> new LocalDateTime[]{ now.minusDays(30), null };
            case "60d"   -> new LocalDateTime[]{ now.minusDays(60), null };
            case "90d"   -> new LocalDateTime[]{ now.minusDays(90), null };
            case "180d"  -> new LocalDateTime[]{ now.minusDays(180), null };
            case "all"   -> new LocalDateTime[]{ null, null };
            case "custom" -> new LocalDateTime[]{
                    startDate != null ? startDate.atStartOfDay() : null,
                    endDate   != null ? endDate.atTime(23, 59, 59) : null
            };
            default      -> new LocalDateTime[]{ now.minusDays(30), null };
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 전략 그루핑 키
    // ──────────────────────────────────────────────────────────────────────────

    private record StrategyKey(
            List<String> indicators,
            List<String> technicalAnalyses,
            List<String> timeframes,
            PositionSide side,
            MarketCondition marketCondition
    ) {}
}
