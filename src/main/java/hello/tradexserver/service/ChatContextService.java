package hello.tradexserver.service;

import hello.tradexserver.domain.RiskPattern;
import hello.tradexserver.domain.StrategyPattern;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.dto.chat.JournalSearchRequest;
import hello.tradexserver.dto.chat.JournalSearchResponse;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.RiskPatternRepository;
import hello.tradexserver.repository.StrategyPatternRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatContextService {

    private final PositionRepository positionRepository;
    private final StrategyPatternRepository strategyPatternRepository;
    private final RiskPatternRepository riskPatternRepository;
    private final TradingJournalRepository tradingJournalRepository;

    public SystemMessage buildSystemMessage(Long userId) {
        // 전체 통합 통계 (exchangeName = null)
        Object[] summaryStats = positionRepository.getFuturesSummaryStats(userId, "CLOSED", null, null);
        Object[] closedSummary = positionRepository.getClosedPositionsSummary(userId, "CLOSED", null, null);
        List<Object[]> profitRanking = positionRepository.getProfitRankingBySymbol(userId, "CLOSED", null, null);

        // 전략 패턴 상위 3
        List<StrategyPattern> strategies = strategyPatternRepository.findTop3ByUserIdOrderByWinRateDesc(userId);

        // 리스크 패턴 (전체 통합)
        Optional<RiskPattern> riskPatternOpt = riskPatternRepository.findByUserIdAndExchangeNameIsNull(userId);

        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 전문 트레이딩 코치입니다. 아래는 이 트레이더의 매매 데이터입니다.\n");
        prompt.append("이 데이터를 참고하여 개인화된 조언을 제공하세요.\n");
        prompt.append("매매일지에 대한 구체적인 질문이 들어오면 searchTradingJournals 함수를 호출하여 관련 매매일지를 조회하세요.\n\n");

        // 매매 요약
        buildSummarySection(prompt, summaryStats);

        // 롱/숏 성과
        buildClosedSummarySection(prompt, closedSummary);

        // 페어별 손익 (상위 5)
        buildPairRankingSection(prompt, profitRanking);

        // 전략 패턴
        buildStrategySection(prompt, strategies);

        // 리스크 패턴
        buildRiskSection(prompt, riskPatternOpt);

        return new SystemMessage(prompt.toString());
    }

    public JournalSearchResponse searchJournals(Long userId, JournalSearchRequest request) {
        log.info("[Function Call] searchTradingJournals 호출 - userId: {}, symbol: {}, startDate: {}",
                userId, request.symbol(), request.startDate());
        String symbol = request.symbol();
        LocalDateTime startDate = null;

        if (request.startDate() != null && !request.startDate().isBlank()) {
            try {
                startDate = LocalDate.parse(request.startDate()).atStartOfDay();
            } catch (DateTimeParseException e) {
                log.warn("Invalid startDate format: {}", request.startDate());
            }
        }

        List<TradingJournal> journals = tradingJournalRepository.searchJournals(
                userId, symbol, startDate, PageRequest.of(0, 10));

        List<JournalSearchResponse.JournalSummary> summaries = journals.stream()
                .map(tj -> new JournalSearchResponse.JournalSummary(
                        tj.getPosition().getSymbol(),
                        tj.getPosition().getSide() != null ? tj.getPosition().getSide().name() : null,
                        tj.getPosition().getRealizedPnl() != null ? tj.getPosition().getRealizedPnl().toPlainString() : "0",
                        tj.getPosition().getExitTime() != null ? tj.getPosition().getExitTime().toString() : null,
                        tj.getEntryScenario(),
                        tj.getExitReview(),
                        tj.getRefinedJournal() != null ? tj.getRefinedJournal().getRefinedText() : null
                ))
                .collect(Collectors.toList());

        return new JournalSearchResponse(summaries);
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

    private void buildStrategySection(StringBuilder prompt, List<StrategyPattern> strategies) {
        prompt.append("## 전략 패턴 (상위 3)\n");
        if (strategies != null && !strategies.isEmpty()) {
            for (StrategyPattern sp : strategies) {
                prompt.append("- ").append(sp.getTradingStyle() != null ? sp.getTradingStyle() : "미분류")
                        .append(" / ").append(sp.getPositionSide() != null ? sp.getPositionSide() : "-")
                        .append(" / ").append(sp.getMarketCondition() != null ? sp.getMarketCondition() : "-")
                        .append(" → 승률 ").append(sp.getWinRate() != null ? sp.getWinRate() : "0").append("%")
                        .append(" (").append(sp.getTotalTrades()).append("건)\n");
            }
        } else {
            prompt.append("- 데이터 없음\n");
        }
        prompt.append("\n");
    }

    private void buildRiskSection(StringBuilder prompt, Optional<RiskPattern> riskPatternOpt) {
        prompt.append("## 리스크 패턴\n");
        if (riskPatternOpt.isPresent()) {
            RiskPattern rp = riskPatternOpt.get();
            prompt.append("- 감정적 매매: ").append(rp.getEmotionalTradeCount()).append("건\n");
            prompt.append("- 비계획 진입: ").append(rp.getUnplannedEntryCount()).append("건\n");
            prompt.append("- 손절 위반: ").append(rp.getSlViolationCount()).append("건\n");
            prompt.append("- 이른 익절: ").append(rp.getEarlyTpCount()).append("건\n");
            prompt.append("- 물타기: ").append(rp.getAveragingDownCount()).append("건\n");
        } else {
            prompt.append("- 데이터 없음\n");
        }
    }
}
