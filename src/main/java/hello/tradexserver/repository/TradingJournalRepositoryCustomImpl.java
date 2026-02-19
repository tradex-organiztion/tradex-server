package hello.tradexserver.repository;

import hello.tradexserver.dto.request.JournalStatsFilterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TradingJournalRepositoryCustomImpl implements TradingJournalRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Object[] getJournalStats(Long userId, JournalStatsFilterRequest filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS total_trades,
                    SUM(CASE WHEN p.realized_pnl > 0 THEN 1 ELSE 0 END) AS win_count,
                    SUM(CASE WHEN p.realized_pnl <= 0 THEN 1 ELSE 0 END) AS loss_count,
                    AVG(p.realized_pnl) AS avg_pnl,
                    AVG(
                        CASE WHEN p.leverage IS NOT NULL
                                  AND p.avg_entry_price IS NOT NULL
                                  AND p.closed_size IS NOT NULL
                                  AND p.closed_size != 0
                        THEN (p.realized_pnl * p.leverage / (p.avg_entry_price * p.closed_size)) * 100
                        ELSE NULL END
                    ) AS avg_roi
                FROM trading_journals tj
                JOIN positions p ON tj.position_id = p.id
                WHERE tj.user_id = ?
                  AND p.status = 'CLOSED'
                """);

        List<Object> params = new ArrayList<>();
        params.add(userId);

        // indicators AND 교집합 필터
        if (filter.getIndicators() != null && !filter.getIndicators().isEmpty()) {
            List<String> vals = filter.getIndicators();
            String placeholders = vals.stream().map(v -> "?").collect(Collectors.joining(", "));
            sql.append(String.format("""
                    AND tj.id IN (
                        SELECT ji.journal_id FROM journal_indicators ji
                        WHERE ji.indicator IN (%s)
                        GROUP BY ji.journal_id
                        HAVING COUNT(DISTINCT ji.indicator) = ?
                    )
                    """, placeholders));
            params.addAll(vals);
            params.add(vals.size());
        }

        // timeframes AND 교집합 필터
        if (filter.getTimeframes() != null && !filter.getTimeframes().isEmpty()) {
            List<String> vals = filter.getTimeframes();
            String placeholders = vals.stream().map(v -> "?").collect(Collectors.joining(", "));
            sql.append(String.format("""
                    AND tj.id IN (
                        SELECT jt.journal_id FROM journal_timeframes jt
                        WHERE jt.timeframe IN (%s)
                        GROUP BY jt.journal_id
                        HAVING COUNT(DISTINCT jt.timeframe) = ?
                    )
                    """, placeholders));
            params.addAll(vals);
            params.add(vals.size());
        }

        // technicalAnalyses AND 교집합 필터
        if (filter.getTechnicalAnalyses() != null && !filter.getTechnicalAnalyses().isEmpty()) {
            List<String> vals = filter.getTechnicalAnalyses();
            String placeholders = vals.stream().map(v -> "?").collect(Collectors.joining(", "));
            sql.append(String.format("""
                    AND tj.id IN (
                        SELECT jta.journal_id FROM journal_technical_analyses jta
                        WHERE jta.technical_analysis IN (%s)
                        GROUP BY jta.journal_id
                        HAVING COUNT(DISTINCT jta.technical_analysis) = ?
                    )
                    """, placeholders));
            params.addAll(vals);
            params.add(vals.size());
        }

        // positionSide 필터
        if (filter.getPositionSide() != null) {
            sql.append("AND p.side = ? ");
            params.add(filter.getPositionSide().name());
        }

        // marketCondition 필터
        if (filter.getMarketCondition() != null) {
            sql.append("AND p.market_condition = ? ");
            params.add(filter.getMarketCondition().name());
        }

        // tradingStyle 필터 (DB 저장 없이 보유시간으로 계산, 1일 = 86400초 기준)
        if ("SCALPING".equals(filter.getTradingStyle())) {
            sql.append("AND EXTRACT(EPOCH FROM (p.exit_time - p.entry_time)) < 86400 ");
        } else if ("SWING".equals(filter.getTradingStyle())) {
            sql.append("AND EXTRACT(EPOCH FROM (p.exit_time - p.entry_time)) >= 86400 ");
        }

        return jdbcTemplate.queryForObject(sql.toString(),
                (rs, rowNum) -> new Object[]{
                        rs.getLong("total_trades"),
                        rs.getLong("win_count"),
                        rs.getLong("loss_count"),
                        rs.getBigDecimal("avg_pnl"),
                        rs.getBigDecimal("avg_roi")
                },
                params.toArray());
    }
}
