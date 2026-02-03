package hello.tradexserver.service;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.dto.response.futures.*;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FuturesService {

    private final PositionRepository positionRepository;

    /**
     * 선물 거래 요약 조회
     * - 총 손익, 거래 규모, 승률, 손익 차트
     */
    public FuturesSummaryResponse getFuturesSummary(Long userId, String period) {
        LocalDateTime startDate = getStartDateFromPeriod(period);

        // 요약 통계 조회
        Object[] stats = positionRepository.getFuturesSummaryStats(userId, "CLOSED", startDate);

        BigDecimal totalPnl = BigDecimal.ZERO;
        int winCount = 0;
        int lossCount = 0;
        int totalCount = 0;

        if (stats != null && stats.length > 0) {
            Object[] row = stats[0] instanceof Object[] ? (Object[]) stats[0] : stats;
            totalPnl = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
            winCount = row[1] != null ? ((Number) row[1]).intValue() : 0;
            lossCount = row[2] != null ? ((Number) row[2]).intValue() : 0;
            totalCount = row[3] != null ? ((Number) row[3]).intValue() : 0;
        }

        // 거래 규모 조회
        BigDecimal totalVolume = positionRepository.getTotalVolume(userId, "CLOSED", startDate);
        if (totalVolume == null) {
            totalVolume = BigDecimal.ZERO;
        }

        // 승률 계산
        BigDecimal winRate = BigDecimal.ZERO;
        if (totalCount > 0) {
            winRate = BigDecimal.valueOf(winCount)
                    .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // 손익 차트 데이터 (기간 내 종료 포지션 기준)
        List<Position> positions = positionRepository.findClosedPositionsByPeriod(
                userId, PositionStatus.CLOSED, startDate);

        List<FuturesSummaryResponse.PnlChartData> pnlChart = buildPnlChart(positions);

        return FuturesSummaryResponse.builder()
                .totalPnl(totalPnl.setScale(2, RoundingMode.HALF_UP))
                .totalVolume(totalVolume.setScale(2, RoundingMode.HALF_UP))
                .winRate(winRate.setScale(2, RoundingMode.HALF_UP))
                .winCount(winCount)
                .lossCount(lossCount)
                .totalTradeCount(totalCount)
                .pnlChart(pnlChart)
                .build();
    }

    /**
     * 페어별 손익 랭킹 조회
     */
    public ProfitRankingResponse getProfitRanking(Long userId, String period) {
        LocalDateTime startDate = getStartDateFromPeriod(period);

        List<Object[]> rankings = positionRepository.getProfitRankingBySymbol(userId, "CLOSED", startDate);

        List<ProfitRankingResponse.PairProfit> pairProfits = new ArrayList<>();
        int rank = 1;

        for (Object[] row : rankings) {
            String symbol = row[0] != null ? row[0].toString() : "";
            BigDecimal totalPnl = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            int tradeCount = row[2] != null ? ((Number) row[2]).intValue() : 0;
            int winCount = row[3] != null ? ((Number) row[3]).intValue() : 0;

            BigDecimal winRate = BigDecimal.ZERO;
            if (tradeCount > 0) {
                winRate = BigDecimal.valueOf(winCount)
                        .divide(BigDecimal.valueOf(tradeCount), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            pairProfits.add(ProfitRankingResponse.PairProfit.builder()
                    .rank(rank++)
                    .symbol(symbol)
                    .totalPnl(totalPnl.setScale(2, RoundingMode.HALF_UP))
                    .tradeCount(tradeCount)
                    .winRate(winRate.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return ProfitRankingResponse.builder()
                .rankings(pairProfits)
                .build();
    }

    /**
     * 종료 포지션 요약 조회
     */
    public ClosedPositionsSummaryResponse getClosedPositionsSummary(Long userId, String period) {
        LocalDateTime startDate = getStartDateFromPeriod(period);

        Object[] stats = positionRepository.getClosedPositionsSummary(userId, "CLOSED", startDate);

        int totalCount = 0;
        int winCount = 0;
        BigDecimal longPnl = BigDecimal.ZERO;
        int longCount = 0;
        BigDecimal shortPnl = BigDecimal.ZERO;
        int shortCount = 0;

        if (stats != null && stats.length > 0) {
            Object[] row = stats[0] instanceof Object[] ? (Object[]) stats[0] : stats;
            totalCount = row[0] != null ? ((Number) row[0]).intValue() : 0;
            winCount = row[1] != null ? ((Number) row[1]).intValue() : 0;
            longPnl = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            longCount = row[3] != null ? ((Number) row[3]).intValue() : 0;
            shortPnl = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
            shortCount = row[5] != null ? ((Number) row[5]).intValue() : 0;
        }

        BigDecimal winRate = BigDecimal.ZERO;
        if (totalCount > 0) {
            winRate = BigDecimal.valueOf(winCount)
                    .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return ClosedPositionsSummaryResponse.builder()
                .totalClosedCount(totalCount)
                .winRate(winRate.setScale(2, RoundingMode.HALF_UP))
                .longPnl(longPnl.setScale(2, RoundingMode.HALF_UP))
                .longCount(longCount)
                .shortPnl(shortPnl.setScale(2, RoundingMode.HALF_UP))
                .shortCount(shortCount)
                .build();
    }

    /**
     * 종료 포지션 목록 조회
     */
    public Page<ClosedPositionResponse> getClosedPositions(Long userId, String symbol,
                                                            PositionSide side, Pageable pageable) {
        Page<Position> positions;

        if (symbol != null && side != null) {
            positions = positionRepository.findByUserIdAndStatusAndSymbolAndSide(
                    userId, PositionStatus.CLOSED, symbol, side, pageable);
        } else if (symbol != null) {
            positions = positionRepository.findByUserIdAndStatusAndSymbol(
                    userId, PositionStatus.CLOSED, symbol, pageable);
        } else if (side != null) {
            positions = positionRepository.findByUserIdAndStatusAndSide(
                    userId, PositionStatus.CLOSED, side, pageable);
        } else {
            positions = positionRepository.findByUserIdAndStatus(
                    userId, PositionStatus.CLOSED, pageable);
        }

        return positions.map(ClosedPositionResponse::from);
    }

    private LocalDateTime getStartDateFromPeriod(String period) {
        if (period == null || period.equals("all")) {
            return null;
        }

        LocalDate today = LocalDate.now();
        int days = switch (period) {
            case "7d" -> 7;
            case "30d" -> 30;
            case "60d" -> 60;
            case "90d" -> 90;
            case "180d" -> 180;
            default -> 0;
        };

        if (days == 0) {
            return null;
        }

        return today.minusDays(days - 1).atStartOfDay();
    }

    private List<FuturesSummaryResponse.PnlChartData> buildPnlChart(List<Position> positions) {
        // 일별로 그룹핑
        Map<LocalDate, BigDecimal> dailyPnl = positions.stream()
                .filter(p -> p.getExitTime() != null && p.getRealizedPnl() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getExitTime().toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Position::getRealizedPnl,
                                BigDecimal::add)
                ));

        List<FuturesSummaryResponse.PnlChartData> chartData = new ArrayList<>();
        BigDecimal cumulativePnl = BigDecimal.ZERO;

        List<LocalDate> sortedDates = dailyPnl.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (LocalDate date : sortedDates) {
            BigDecimal pnl = dailyPnl.get(date);
            cumulativePnl = cumulativePnl.add(pnl);

            chartData.add(FuturesSummaryResponse.PnlChartData.builder()
                    .date(date)
                    .pnl(pnl.setScale(2, RoundingMode.HALF_UP))
                    .cumulativePnl(cumulativePnl.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return chartData;
    }
}
