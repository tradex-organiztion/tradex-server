package hello.tradexserver.service;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.dto.response.strategy.StrategyAnalysisResponse;
import hello.tradexserver.dto.response.strategy.StrategyAnalysisResponse.StrategyItem;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
