package hello.tradexserver.service;

import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.dto.chat.JournalSearchRequest;
import hello.tradexserver.dto.chat.JournalSearchResponse;
import hello.tradexserver.dto.chat.JournalStatsRequest;
import hello.tradexserver.dto.chat.JournalStatsResponse;
import hello.tradexserver.dto.response.risk.RiskAnalysisResponse;
import hello.tradexserver.dto.response.strategy.StrategyAnalysisResponse;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatContextService {

    private final PositionRepository positionRepository;
    private final TradingJournalRepository tradingJournalRepository;
    private final StrategyAnalysisService strategyAnalysisService;
    private final RiskAnalysisService riskAnalysisService;

    public SystemMessage buildSystemMessage(Long userId) {
        // 전체 통합 통계 (exchangeName = null)
        Object[] summaryStats = positionRepository.getFuturesSummaryStats(userId, "CLOSED", null, null);
        Object[] closedSummary = positionRepository.getClosedPositionsSummary(userId, "CLOSED", null, null);
        List<Object[]> profitRanking = positionRepository.getProfitRankingBySymbol(userId, "CLOSED", null, null);

        // 전략 패턴 상위 3 (전체 기간, 전 거래소)
        List<StrategyAnalysisResponse.StrategyItem> strategies = strategyAnalysisService
                .analyze(userId, null, "all", null, null)
                .getStrategies().stream()
                .limit(3)
                .toList();

        // 리스크 패턴 (전체 기간, 전 거래소)
        RiskAnalysisResponse riskAnalysis = riskAnalysisService.analyze(userId, null, "all", null, null);

        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 전문 트레이딩 코치입니다. 아래는 이 트레이더의 매매 데이터입니다.\n");
        prompt.append("이 데이터를 참고하여 개인화된 조언을 제공하세요.\n");
        prompt.append("매매일지 상세 내용이 필요하면 searchTradingJournals 함수를, 집계/통계가 필요하면 getJournalStats 함수를 호출하세요.\n\n");

        buildSummarySection(prompt, summaryStats);
        buildClosedSummarySection(prompt, closedSummary);
        buildPairRankingSection(prompt, profitRanking);
        buildStrategySection(prompt, strategies);
        buildRiskSection(prompt, riskAnalysis);

        return new SystemMessage(prompt.toString());
    }

    public JournalSearchResponse searchJournals(Long userId, JournalSearchRequest request) {
        log.info("[Function Call] searchTradingJournals - userId: {}, symbol: {}, side: {}, exchange: {}, " +
                        "startDate: {}, endDate: {}, minPnl: {}, maxPnl: {}, winOnly: {}, " +
                        "isEmotionalTrade: {}, isUnplannedEntry: {}, hasReview: {}, sortBy: {}, limit: {}",
                userId, request.symbol(), request.side(), request.exchangeName(),
                request.startDate(), request.endDate(), request.minPnl(), request.maxPnl(), request.winOnly(),
                request.isEmotionalTrade(), request.isUnplannedEntry(), request.hasReview(),
                request.sortBy(), request.limit());

        PositionSide side = parseEnum(PositionSide.class, request.side(), "side");
        ExchangeName exchangeName = parseEnum(ExchangeName.class, request.exchangeName(), "exchangeName");
        LocalDateTime startDate = parseDate(request.startDate(), "startDate", false);
        LocalDateTime endDate = parseDate(request.endDate(), "endDate", true);
        BigDecimal minPnl = request.minPnl() != null ? BigDecimal.valueOf(request.minPnl()) : null;
        BigDecimal maxPnl = request.maxPnl() != null ? BigDecimal.valueOf(request.maxPnl()) : null;
        boolean pnlPositive = Boolean.TRUE.equals(request.winOnly());
        boolean pnlNegative = Boolean.FALSE.equals(request.winOnly());
        int limit = Math.min(request.limit() != null && request.limit() > 0 ? request.limit() : 20, 50);

        Sort sort = "pnl".equals(request.sortBy())
                ? Sort.by(Sort.Direction.DESC, "position.realizedPnl")
                : "entryTime".equals(request.sortBy())
                ? Sort.by(Sort.Direction.DESC, "position.entryTime")
                : Sort.by(Sort.Direction.DESC, "position.exitTime");

        List<TradingJournal> journals = tradingJournalRepository.searchJournals(
                userId, request.symbol(), side, exchangeName,
                startDate, endDate, minPnl, maxPnl,
                pnlPositive, pnlNegative,
                request.isEmotionalTrade(), request.isUnplannedEntry(), request.hasReview(),
                PageRequest.of(0, limit, sort));

        List<JournalSearchResponse.JournalSummary> summaries = journals.stream()
                .map(tj -> new JournalSearchResponse.JournalSummary(
                        tj.getPosition().getSymbol(),
                        tj.getPosition().getSide() != null ? tj.getPosition().getSide().name() : null,
                        tj.getPosition().getExchangeName() != null ? tj.getPosition().getExchangeName().name() : null,
                        tj.getPosition().getLeverage(),
                        tj.getPosition().getRealizedPnl() != null ? tj.getPosition().getRealizedPnl().toPlainString() : "0",
                        tj.getPosition().getEntryTime() != null ? tj.getPosition().getEntryTime().toString() : null,
                        tj.getPosition().getExitTime() != null ? tj.getPosition().getExitTime().toString() : null,
                        tj.getIndicators(),
                        tj.getTimeframes(),
                        tj.getEntryReason(),
                        tj.getReviewContent(),
                        tj.getRefinedJournal() != null ? tj.getRefinedJournal().getRefinedText() : null,
                        tj.getRefinedJournal() != null ? tj.getRefinedJournal().getIsEmotionalTrade() : null,
                        tj.getRefinedJournal() != null ? tj.getRefinedJournal().getIsUnplannedEntry() : null
                ))
                .collect(Collectors.toList());

        return new JournalSearchResponse(summaries.size(), summaries);
    }

    public JournalStatsResponse getJournalStats(Long userId, JournalStatsRequest request) {
        log.info("[Function Call] getJournalStats - userId: {}, symbol: {}, side: {}, exchange: {}, startDate: {}, endDate: {}",
                userId, request.symbol(), request.side(), request.exchangeName(), request.startDate(), request.endDate());

        LocalDateTime startDate = parseDate(request.startDate(), "startDate", false);
        LocalDateTime endDate = parseDate(request.endDate(), "endDate", true);

        Object[] row = positionRepository.getFilteredStats(
                userId, request.symbol(),
                request.side() != null ? request.side().toUpperCase() : null,
                request.exchangeName() != null ? request.exchangeName().toUpperCase() : null,
                startDate, endDate);

        if (row == null || row[0] == null) {
            return new JournalStatsResponse(0, 0, 0, "0.00%", "0", "0", "0", "0");
        }

        int totalCount = ((Number) row[0]).intValue();
        int winCount = row[1] != null ? ((Number) row[1]).intValue() : 0;
        int lossCount = row[2] != null ? ((Number) row[2]).intValue() : 0;
        BigDecimal totalPnl = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
        BigDecimal avgPnl = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
        BigDecimal maxWin = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
        BigDecimal maxLoss = row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO;

        String winRate = totalCount > 0
                ? BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%"
                : "0.00%";

        return new JournalStatsResponse(
                totalCount, winCount, lossCount, winRate,
                totalPnl.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                avgPnl.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                maxWin.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                maxLoss.setScale(2, RoundingMode.HALF_UP).toPlainString()
        );
    }

    private LocalDateTime parseDate(String dateStr, String fieldName, boolean endOfDay) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            log.warn("Invalid {} format: {}", fieldName, dateStr);
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, String fieldName) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid {} value: {}", fieldName, value);
            return null;
        }
    }

    private void buildSummarySection(StringBuilder prompt, Object[] stats) {
        prompt.append("## 매매 요약\n");
        if (stats != null && stats.length > 0) {
            Object[] row = stats[0] instanceof Object[] ? (Object[]) stats[0] : stats;
            BigDecimal totalPnl = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
            int winCount = row[1] != null ? ((Number) row[1]).intValue() : 0;
            int lossCount = row[2] != null ? ((Number) row[2]).intValue() : 0;
            int totalCount = row[3] != null ? ((Number) row[3]).intValue() : 0;

            BigDecimal winRate = BigDecimal.ZERO;
            if (totalCount > 0) {
                winRate = BigDecimal.valueOf(winCount)
                        .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            prompt.append("- 총 실현 손익: ").append(totalPnl.setScale(2, RoundingMode.HALF_UP)).append(" USDT\n");
            prompt.append("- 승률: ").append(winRate.setScale(2, RoundingMode.HALF_UP)).append("% (")
                    .append(winCount).append("승 ").append(lossCount).append("패)\n");
            prompt.append("- 총 거래 수: ").append(totalCount).append("\n");
        } else {
            prompt.append("- 데이터 없음\n");
        }
        prompt.append("\n");
    }

    private void buildClosedSummarySection(StringBuilder prompt, Object[] stats) {
        prompt.append("## 롱/숏 성과\n");
        if (stats != null && stats.length > 0) {
            Object[] row = stats[0] instanceof Object[] ? (Object[]) stats[0] : stats;
            BigDecimal longPnl = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            int longCount = row[3] != null ? ((Number) row[3]).intValue() : 0;
            BigDecimal shortPnl = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
            int shortCount = row[5] != null ? ((Number) row[5]).intValue() : 0;

            prompt.append("- 롱: ").append(longPnl.setScale(2, RoundingMode.HALF_UP)).append(" USDT (")
                    .append(longCount).append("건)\n");
            prompt.append("- 숏: ").append(shortPnl.setScale(2, RoundingMode.HALF_UP)).append(" USDT (")
                    .append(shortCount).append("건)\n");
        } else {
            prompt.append("- 데이터 없음\n");
        }
        prompt.append("\n");
    }

    private void buildPairRankingSection(StringBuilder prompt, List<Object[]> rankings) {
        prompt.append("## 페어별 손익 (상위 5)\n");
        if (rankings != null && !rankings.isEmpty()) {
            int limit = Math.min(rankings.size(), 5);
            for (int i = 0; i < limit; i++) {
                Object[] row = rankings.get(i);
                String symbol = row[0] != null ? row[0].toString() : "";
                BigDecimal pnl = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                int tradeCount = row[2] != null ? ((Number) row[2]).intValue() : 0;
                prompt.append("- ").append(symbol).append(": ")
                        .append(pnl.setScale(2, RoundingMode.HALF_UP)).append(" USDT (")
                        .append(tradeCount).append("건)\n");
            }
        } else {
            prompt.append("- 데이터 없음\n");
        }
        prompt.append("\n");
    }

    private void buildStrategySection(StringBuilder prompt, List<StrategyAnalysisResponse.StrategyItem> strategies) {
        prompt.append("## 전략 패턴 (승률 상위 3)\n");
        if (strategies != null && !strategies.isEmpty()) {
            for (StrategyAnalysisResponse.StrategyItem s : strategies) {
                prompt.append("- 지표: ").append(!s.getIndicators().isEmpty() ? s.getIndicators() : "미기록")
                        .append(" / ").append(s.getSide() != null ? s.getSide() : "-")
                        .append(" / ").append(s.getMarketCondition() != null ? s.getMarketCondition() : "-")
                        .append(" → 승률 ").append(s.getWinRate() != null ? s.getWinRate() : "0").append("%")
                        .append(" (").append(s.getTotalTrades()).append("건)\n");
            }
        } else {
            prompt.append("- 데이터 없음\n");
        }
        prompt.append("\n");
    }

    private void buildRiskSection(StringBuilder prompt, RiskAnalysisResponse risk) {
        prompt.append("## 리스크 패턴\n");
        if (risk != null && risk.getTotalTrades() > 0) {
            prompt.append("- 감정적 매매: ").append(risk.getEmotionalRisk().getEmotionalTradeCount()).append("건\n");
            prompt.append("- 비계획 진입: ").append(risk.getEntryRisk().getUnplannedEntryCount()).append("건\n");
            prompt.append("- 손절 위반: ").append(risk.getExitRisk().getSlViolationCount()).append("건\n");
            prompt.append("- 이른 익절: ").append(risk.getExitRisk().getEarlyTpCount()).append("건\n");
            prompt.append("- 물타기: ").append(risk.getPositionManagementRisk().getAveragingDownCount()).append("건\n");
        } else {
            prompt.append("- 데이터 없음\n");
        }
    }
}
