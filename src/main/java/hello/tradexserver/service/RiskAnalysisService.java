package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.dto.response.risk.*;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskAnalysisService {

    private static final BigDecimal SL_TOLERANCE = new BigDecimal("0.003"); // 0.3%
    private static final long REENTRY_WINDOW_MINUTES = 15;

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;

    public RiskAnalysisResponse analyze(Long userId, String exchangeName, String period,
                                        LocalDate startDate, LocalDate endDate) {
        ExchangeName exchangeEnum = exchangeName != null ? ExchangeName.valueOf(exchangeName) : null;
        LocalDateTime[] range = resolveRange(period, startDate, endDate);
        LocalDateTime from = range[0];
        LocalDateTime to = range[1];

        // Query 1: Position + TradingJournal (JOIN FETCH, 단일 쿼리)
        List<Position> positions = positionRepository
                .findClosedWithJournalForRiskAnalysis(userId, exchangeEnum, from, to);

        if (positions.isEmpty()) {
            return RiskAnalysisResponse.empty();
        }

        // Query 2: 해당 포지션들의 진입 오더 bulk 조회 (물타기 계산용)
        List<Long> positionIds = positions.stream().map(Position::getId).toList();
        List<Order> openOrders = orderRepository.findOpenOrdersByPositionIds(positionIds);

        // 이후 모든 분석은 in-memory 처리 — 추가 쿼리 없음
        Map<Long, List<Order>> ordersByPosition = openOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getPosition().getId()));

        int total = positions.size();

        return RiskAnalysisResponse.builder()
                .totalTrades(total)
                .entryRisk(analyzeEntryRisk(positions, total))
                .exitRisk(analyzeExitRisk(positions, total))
                .positionManagementRisk(analyzePositionManagementRisk(positions, ordersByPosition))
                .timeRisk(analyzeTimeRisk(positions))
                .emotionalRisk(analyzeEmotionalRisk(positions, total))
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1️⃣ 진입 리스크
    // ──────────────────────────────────────────────────────────────────────────

    private EntryRiskResponse analyzeEntryRisk(List<Position> positions, int total) {
        int unplannedCount = 0;
        int unplannedWin = 0;
        int plannedWin = 0;
        int plannedCount = 0;

        for (Position p : positions) {
            TradingJournal journal = p.getTradingJournal();
            boolean isUnplanned = journal == null || !StringUtils.hasText(journal.getEntryScenario());

            if (isUnplanned) {
                unplannedCount++;
                if (isWin(p)) unplannedWin++;
            } else {
                plannedCount++;
                if (isWin(p)) plannedWin++;
            }
        }

        int emotionalReEntryCount = countEmotionalReEntry(positions);
        int impulsiveTradeCount = countImpulsiveTrades(positions);

        return EntryRiskResponse.builder()
                .unplannedEntryCount(unplannedCount)
                .unplannedEntryRate(rate(unplannedCount, total))
                .unplannedEntryWinRate(rate(unplannedWin, unplannedCount))
                .plannedEntryWinRate(rate(plannedWin, plannedCount))
                .emotionalReEntryCount(emotionalReEntryCount)
                .emotionalReEntryRate(rate(emotionalReEntryCount, total))
                .impulsiveTradeCount(impulsiveTradeCount)
                .impulsiveTradeRate(rate(impulsiveTradeCount, total))
                .build();
    }

    /**
     * 손절 후 즉시 재진입 카운트
     * - 이전 포지션 PnL < 0
     * - 이전 포지션 종료 후 15분 이내 동일 symbol 신규 진입
     * - 새 포지션에 entryScenario가 없는 경우만 감정 매매로 판단
     */
    private int countEmotionalReEntry(List<Position> positions) {
        // 포지션은 entryTime ASC로 이미 정렬되어 있음
        // symbol별로 그룹핑하여 연속된 포지션 체크
        Map<String, List<Position>> bySymbol = positions.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getExchangeName() + ":" + p.getSymbol(),
                        Collectors.toList()
                ));

        int count = 0;
        for (List<Position> symbolPositions : bySymbol.values()) {
            for (int i = 1; i < symbolPositions.size(); i++) {
                Position prev = symbolPositions.get(i - 1);
                Position curr = symbolPositions.get(i);

                if (prev.getExitTime() == null) continue;
                long minutesBetween = Duration.between(prev.getExitTime(), curr.getEntryTime()).toMinutes();

                boolean prevLoss = prev.getRealizedPnl() != null
                        && prev.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0;
                boolean withinWindow = minutesBetween >= 0 && minutesBetween <= REENTRY_WINDOW_MINUTES;
                boolean noScenario = curr.getTradingJournal() == null
                        || !StringUtils.hasText(curr.getTradingJournal().getEntryScenario());

                if (prevLoss && withinWindow && noScenario) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 연속 진입(뇌동 매매) 카운트
     * - 동일 symbol에서 "이전 포지션 종료 → 15분 이내 재진입" 체인 길이가 3 이상인 경우
     * - 체인에 속한 포지션 수를 합산하여 반환
     */
    private int countImpulsiveTrades(List<Position> positions) {
        Map<String, List<Position>> bySymbol = positions.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getExchangeName() + ":" + p.getSymbol(),
                        Collectors.toList()
                ));

        int count = 0;
        for (List<Position> symbolPositions : bySymbol.values()) {
            int chainLen = 1;
            for (int i = 1; i < symbolPositions.size(); i++) {
                Position prev = symbolPositions.get(i - 1);
                Position curr = symbolPositions.get(i);

                if (prev.getExitTime() != null) {
                    long minutes = Duration.between(prev.getExitTime(), curr.getEntryTime()).toMinutes();
                    if (minutes >= 0 && minutes <= REENTRY_WINDOW_MINUTES) {
                        chainLen++;
                        continue;
                    }
                }
                // 체인 끊김
                if (chainLen >= 3) count += chainLen;
                chainLen = 1;
            }
            // 마지막 체인 처리
            if (chainLen >= 3) count += chainLen;
        }
        return count;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2️⃣ 청산 리스크
    // ──────────────────────────────────────────────────────────────────────────

    private ExitRiskResponse analyzeExitRisk(List<Position> positions, int total) {
        int slWithTarget = 0;       // SL이 설정된 포지션 수
        int slViolationCount = 0;
        BigDecimal slDelaySum = BigDecimal.ZERO;

        int tpWithTarget = 0;       // TP가 설정된 포지션 수
        int earlyTpCount = 0;

        for (Position p : positions) {
            if (p.getAvgExitPrice() == null) continue;
            TradingJournal journal = p.getTradingJournal();

            // SL 판단
            BigDecimal sl = resolveStopLoss(journal, p);
            if (sl != null) {
                slWithTarget++;
                if (isSlViolated(p, sl)) {
                    slViolationCount++;
                    slDelaySum = slDelaySum.add(calcSlDelay(p, sl));
                }
            }

            // TP 판단
            BigDecimal tp = resolveTakeProfit(journal, p);
            if (tp != null) {
                tpWithTarget++;
                if (isEarlyTp(p, tp)) {
                    earlyTpCount++;
                }
            }
        }

        BigDecimal avgSlDelay = slViolationCount > 0
                ? slDelaySum.divide(BigDecimal.valueOf(slViolationCount), 4, RoundingMode.HALF_UP)
                : null;

        return ExitRiskResponse.builder()
                .slViolationCount(slViolationCount)
                .slViolationRate(rate(slViolationCount, slWithTarget))
                .avgSlDelay(avgSlDelay)
                .earlyTpCount(earlyTpCount)
                .earlyTpRate(rate(earlyTpCount, tpWithTarget))
                .build();
    }

    /** SL 우선순위: 매매일지 plannedStopLoss > Position.stopLossPrice */
    private BigDecimal resolveStopLoss(TradingJournal journal, Position p) {
        if (journal != null && journal.getPlannedStopLoss() != null) {
            return journal.getPlannedStopLoss();
        }
        return p.getStopLossPrice();
    }

    /** TP 우선순위: 매매일지 plannedTargetPrice > Position.targetPrice */
    private BigDecimal resolveTakeProfit(TradingJournal journal, Position p) {
        if (journal != null && journal.getPlannedTargetPrice() != null) {
            return journal.getPlannedTargetPrice();
        }
        return p.getTargetPrice();
    }

    /**
     * SL 미준수 판단 (0.3% 허용 오차)
     * LONG: 실제 청산가 < SL * (1 - 0.003)
     * SHORT: 실제 청산가 > SL * (1 + 0.003)
     */
    private boolean isSlViolated(Position p, BigDecimal sl) {
        BigDecimal exit = p.getAvgExitPrice();
        if (p.getSide() == PositionSide.LONG) {
            return exit.compareTo(sl.multiply(BigDecimal.ONE.subtract(SL_TOLERANCE))) < 0;
        } else {
            return exit.compareTo(sl.multiply(BigDecimal.ONE.add(SL_TOLERANCE))) > 0;
        }
    }

    /**
     * 손절 지연 오차율 (%)
     * LONG: (exit - sl) / sl * 100 → 음수
     * SHORT: (sl - exit) / sl * 100 → 음수
     */
    private BigDecimal calcSlDelay(Position p, BigDecimal sl) {
        BigDecimal exit = p.getAvgExitPrice();
        BigDecimal diff = p.getSide() == PositionSide.LONG
                ? exit.subtract(sl)
                : sl.subtract(exit);
        return diff.divide(sl, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 조기 익절 판단
     * LONG: 실제 청산가 < TP (목표 달성 전 청산)
     * SHORT: 실제 청산가 > TP
     */
    private boolean isEarlyTp(Position p, BigDecimal tp) {
        BigDecimal exit = p.getAvgExitPrice();
        if (p.getSide() == PositionSide.LONG) {
            return exit.compareTo(tp) < 0;
        } else {
            return exit.compareTo(tp) > 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3️⃣ 포지션 관리 리스크
    // ──────────────────────────────────────────────────────────────────────────

    private PositionManagementRiskResponse analyzePositionManagementRisk(
            List<Position> positions, Map<Long, List<Order>> ordersByPosition) {

        // R/R 비율
        BigDecimal avgRrRatio = calcAvgRrRatio(positions);

        // 물타기
        int positionsWithAddEntry = 0;
        int averagingDownCount = 0;

        for (Position p : positions) {
            List<Order> openOrders = ordersByPosition.getOrDefault(p.getId(), List.of());
            if (openOrders.size() <= 1) continue; // 추가 진입 없음

            positionsWithAddEntry++;
            if (hasAveragingDown(p, openOrders)) {
                averagingDownCount++;
            }
        }

        return PositionManagementRiskResponse.builder()
                .avgRrRatio(avgRrRatio)
                .averagingDownCount(averagingDownCount)
                .averagingDownRate(rate(averagingDownCount, positionsWithAddEntry))
                .build();
    }

    private BigDecimal calcAvgRrRatio(List<Position> positions) {
        List<BigDecimal> wins = positions.stream()
                .filter(p -> p.getRealizedPnl() != null && p.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                .map(Position::getRealizedPnl)
                .toList();

        List<BigDecimal> losses = positions.stream()
                .filter(p -> p.getRealizedPnl() != null && p.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0)
                .map(p -> p.getRealizedPnl().abs())
                .toList();

        if (wins.isEmpty() || losses.isEmpty()) return null;

        BigDecimal avgWin = wins.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(wins.size()), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(losses.size()), 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return null;
        return avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP);
    }

    /**
     * 물타기 여부 판단
     * 추가 진입 오더들을 fillTime 순으로 재구성하며 평균 진입가를 업데이트,
     * 추가 진입 시점에 미실현 손실 상태(현재가가 평균 진입가보다 불리)이면 물타기
     *
     * LONG: 추가진입 filledPrice < 현재 avgEntryPrice → 손실 구간
     * SHORT: 추가진입 filledPrice > 현재 avgEntryPrice → 손실 구간
     */
    private boolean hasAveragingDown(Position p, List<Order> openOrders) {
        BigDecimal runningAvgPrice = openOrders.get(0).getFilledPrice();
        BigDecimal runningSize = openOrders.get(0).getFilledQuantity();

        for (int i = 1; i < openOrders.size(); i++) {
            Order addOrder = openOrders.get(i);
            BigDecimal addPrice = addOrder.getFilledPrice();

            boolean isAveragingDown = p.getSide() == PositionSide.LONG
                    ? addPrice.compareTo(runningAvgPrice) < 0
                    : addPrice.compareTo(runningAvgPrice) > 0;

            if (isAveragingDown) return true;

            // 평균 진입가 업데이트
            BigDecimal addQty = addOrder.getFilledQuantity();
            BigDecimal newSize = runningSize.add(addQty);
            runningAvgPrice = runningAvgPrice.multiply(runningSize)
                    .add(addPrice.multiply(addQty))
                    .divide(newSize, 8, RoundingMode.HALF_UP);
            runningSize = newSize;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4️⃣ 시간/상황 리스크
    // ──────────────────────────────────────────────────────────────────────────

    private TimeRiskResponse analyzeTimeRisk(List<Position> positions) {
        // 시간대별 승률
        Map<Integer, List<Position>> byHour = positions.stream()
                .collect(Collectors.groupingBy(p -> p.getEntryTime().getHour()));

        Map<String, BigDecimal> hourlyWinRates = new TreeMap<>();
        byHour.forEach((hour, hourPositions) ->
                hourlyWinRates.put(String.valueOf(hour), calcWinRate(hourPositions)));

        // 시장 상황별 승률
        Map<MarketCondition, List<Position>> byCondition = positions.stream()
                .filter(p -> p.getMarketCondition() != null)
                .collect(Collectors.groupingBy(Position::getMarketCondition));

        BigDecimal uptrendWinRate = calcWinRate(byCondition.get(MarketCondition.UPTREND));
        BigDecimal downtrendWinRate = calcWinRate(byCondition.get(MarketCondition.DOWNTREND));
        BigDecimal sidewaysWinRate = calcWinRate(byCondition.get(MarketCondition.SIDEWAYS));

        return TimeRiskResponse.builder()
                .hourlyWinRates(hourlyWinRates)
                .uptrendWinRate(uptrendWinRate)
                .downtrendWinRate(downtrendWinRate)
                .sidewaysWinRate(sidewaysWinRate)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5️⃣ 감정 리스크
    // ──────────────────────────────────────────────────────────────────────────

    private EmotionalRiskResponse analyzeEmotionalRisk(List<Position> positions, int total) {
        // 감정 매매 = 손절 후 즉시 재진입 (진입 리스크와 동일 로직, 수치 재사용)
        int emotionalCount = countEmotionalReEntry(positions);

        int overconfidentCount = countOverconfidentEntry(positions);
        int immediateReverseCount = countImmediateReverse(positions);

        return EmotionalRiskResponse.builder()
                .emotionalTradeCount(emotionalCount)
                .emotionalTradeRate(rate(emotionalCount, total))
                .overconfidentEntryCount(overconfidentCount)
                .overconfidentEntryRate(rate(overconfidentCount, total))
                .immediateReverseCount(immediateReverseCount)
                .immediateReverseRate(rate(immediateReverseCount, total))
                .build();
    }

    /**
     * 과신 진입 카운트
     * - 직전 포지션 PnL > 0
     * - 15분 이내 재진입
     * - 신규 포지션 PnL < 0 (결과적으로 손실)
     */
    private int countOverconfidentEntry(List<Position> positions) {
        Map<String, List<Position>> bySymbol = positions.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getExchangeName() + ":" + p.getSymbol(),
                        Collectors.toList()
                ));

        int count = 0;
        for (List<Position> symbolPositions : bySymbol.values()) {
            for (int i = 1; i < symbolPositions.size(); i++) {
                Position prev = symbolPositions.get(i - 1);
                Position curr = symbolPositions.get(i);

                if (prev.getExitTime() == null) continue;
                long minutes = Duration.between(prev.getExitTime(), curr.getEntryTime()).toMinutes();

                boolean prevWin = prev.getRealizedPnl() != null
                        && prev.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0;
                boolean withinWindow = minutes >= 0 && minutes <= REENTRY_WINDOW_MINUTES;
                boolean currLoss = curr.getRealizedPnl() != null
                        && curr.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0;

                if (prevWin && withinWindow && currLoss) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 손절 후 즉시 역포지션 카운트
     * - 직전 포지션 PnL < 0
     * - 15분 이내 동일 symbol 반대 방향(side) 진입
     */
    private int countImmediateReverse(List<Position> positions) {
        Map<String, List<Position>> bySymbol = positions.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getExchangeName() + ":" + p.getSymbol(),
                        Collectors.toList()
                ));

        int count = 0;
        for (List<Position> symbolPositions : bySymbol.values()) {
            for (int i = 1; i < symbolPositions.size(); i++) {
                Position prev = symbolPositions.get(i - 1);
                Position curr = symbolPositions.get(i);

                if (prev.getExitTime() == null) continue;
                long minutes = Duration.between(prev.getExitTime(), curr.getEntryTime()).toMinutes();

                boolean prevLoss = prev.getRealizedPnl() != null
                        && prev.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0;
                boolean withinWindow = minutes >= 0 && minutes <= REENTRY_WINDOW_MINUTES;
                boolean reverseSide = curr.getSide() != prev.getSide();

                if (prevLoss && withinWindow && reverseSide) {
                    count++;
                }
            }
        }
        return count;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 유틸리티
    // ──────────────────────────────────────────────────────────────────────────

    private boolean isWin(Position p) {
        return p.getRealizedPnl() != null && p.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0;
    }

    /** 비율 계산: count / total * 100, total이 0이면 null 반환 */
    private BigDecimal rate(int count, int total) {
        if (total == 0) return null;
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 포지션 리스트의 승률 계산, null이면 null 반환 */
    private BigDecimal calcWinRate(List<Position> positions) {
        if (positions == null || positions.isEmpty()) return null;
        long wins = positions.stream().filter(this::isWin).count();
        return rate((int) wins, positions.size());
    }

    /**
     * period 문자열 → [startDate, endDate] 변환
     * - "7d", "30d", "90d" 등: 오늘 기준 N일 전 ~ null(현재)
     * - "custom": 파라미터로 받은 startDate, endDate 사용
     * - "all" 또는 null: null ~ null (전체)
     */
    private LocalDateTime[] resolveRange(String period, LocalDate startDate, LocalDate endDate) {
        if ("custom".equals(period)) {
            LocalDateTime from = startDate != null ? startDate.atStartOfDay() : null;
            LocalDateTime to = endDate != null ? endDate.atTime(23, 59, 59) : null;
            return new LocalDateTime[]{from, to};
        }

        if (period == null || "all".equals(period)) {
            return new LocalDateTime[]{null, null};
        }

        int days = switch (period) {
            case "7d" -> 7;
            case "30d" -> 30;
            case "60d" -> 60;
            case "90d" -> 90;
            case "180d" -> 180;
            default -> 0;
        };

        LocalDateTime from = days > 0
                ? LocalDate.now().minusDays(days - 1).atStartOfDay()
                : null;

        return new LocalDateTime[]{from, null};
    }
}
