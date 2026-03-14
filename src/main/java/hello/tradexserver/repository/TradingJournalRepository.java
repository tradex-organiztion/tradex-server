package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradingJournalRepository extends JpaRepository<TradingJournal, Long>,
        JpaSpecificationExecutor<TradingJournal>, TradingJournalRepositoryCustom {

    Optional<TradingJournal> findByIdAndUserId(Long id, Long userId);

    @Query(value = """
            SELECT DISTINCT ji.indicator
            FROM journal_indicators ji
            JOIN trading_journals tj ON ji.journal_id = tj.id
            WHERE tj.user_id = :userId
            ORDER BY ji.indicator
            """, nativeQuery = true)
    List<String> findDistinctIndicatorsByUser(@Param("userId") Long userId);

    @Query(value = """
            SELECT DISTINCT jt.timeframe
            FROM journal_timeframes jt
            JOIN trading_journals tj ON jt.journal_id = tj.id
            WHERE tj.user_id = :userId
            ORDER BY jt.timeframe
            """, nativeQuery = true)
    List<String> findDistinctTimeframesByUser(@Param("userId") Long userId);

    @Query(value = """
            SELECT DISTINCT jta.technical_analysis
            FROM journal_technical_analyses jta
            JOIN trading_journals tj ON jta.journal_id = tj.id
            WHERE tj.user_id = :userId
            ORDER BY jta.technical_analysis
            """, nativeQuery = true)
    List<String> findDistinctTechnicalAnalysesByUser(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(tj) FROM TradingJournal tj
            JOIN tj.position p
            LEFT JOIN tj.refinedJournal rj
            WHERE tj.user.id = :userId
            AND p.status = 'CLOSED'
            AND (cast(:symbol as string) IS NULL OR p.symbol = :symbol)
            AND (cast(:side as string) IS NULL OR p.side = :side)
            AND (cast(:exchangeName as string) IS NULL OR p.exchangeName = :exchangeName)
            AND (cast(:startDate as LocalDateTime) IS NULL OR p.exitTime >= :startDate)
            AND (cast(:endDate as LocalDateTime) IS NULL OR p.exitTime <= :endDate)
            AND (cast(:minPnl as big_decimal) IS NULL OR p.realizedPnl >= :minPnl)
            AND (cast(:maxPnl as big_decimal) IS NULL OR p.realizedPnl <= :maxPnl)
            AND (:pnlPositive = false OR p.realizedPnl > 0)
            AND (:pnlNegative = false OR p.realizedPnl <= 0)
            AND (cast(:isEmotionalTrade as boolean) IS NULL OR (rj IS NOT NULL AND rj.isEmotionalTrade = :isEmotionalTrade))
            AND (cast(:isUnplannedEntry as boolean) IS NULL OR (rj IS NOT NULL AND rj.isUnplannedEntry = :isUnplannedEntry))
            AND (cast(:hasReview as boolean) IS NULL OR :hasReview = false OR (tj.reviewContent IS NOT NULL AND tj.reviewContent <> ''))
            """)
    long countJournals(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("side") PositionSide side,
            @Param("exchangeName") ExchangeName exchangeName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("minPnl") BigDecimal minPnl,
            @Param("maxPnl") BigDecimal maxPnl,
            @Param("pnlPositive") boolean pnlPositive,
            @Param("pnlNegative") boolean pnlNegative,
            @Param("isEmotionalTrade") Boolean isEmotionalTrade,
            @Param("isUnplannedEntry") Boolean isUnplannedEntry,
            @Param("hasReview") Boolean hasReview);

    @Query("""
            SELECT tj FROM TradingJournal tj
            JOIN FETCH tj.position p
            LEFT JOIN FETCH tj.refinedJournal rj
            WHERE tj.user.id = :userId
            AND p.status = 'CLOSED'
            AND (cast(:symbol as string) IS NULL OR p.symbol = :symbol)
            AND (cast(:side as string) IS NULL OR p.side = :side)
            AND (cast(:exchangeName as string) IS NULL OR p.exchangeName = :exchangeName)
            AND (cast(:startDate as LocalDateTime) IS NULL OR p.exitTime >= :startDate)
            AND (cast(:endDate as LocalDateTime) IS NULL OR p.exitTime <= :endDate)
            AND (cast(:minPnl as big_decimal) IS NULL OR p.realizedPnl >= :minPnl)
            AND (cast(:maxPnl as big_decimal) IS NULL OR p.realizedPnl <= :maxPnl)
            AND (:pnlPositive = false OR p.realizedPnl > 0)
            AND (:pnlNegative = false OR p.realizedPnl <= 0)
            AND (cast(:isEmotionalTrade as boolean) IS NULL OR (rj IS NOT NULL AND rj.isEmotionalTrade = :isEmotionalTrade))
            AND (cast(:isUnplannedEntry as boolean) IS NULL OR (rj IS NOT NULL AND rj.isUnplannedEntry = :isUnplannedEntry))
            AND (cast(:hasReview as boolean) IS NULL OR :hasReview = false OR (tj.reviewContent IS NOT NULL AND tj.reviewContent <> ''))
            """)
    List<TradingJournal> searchJournals(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("side") PositionSide side,
            @Param("exchangeName") ExchangeName exchangeName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("minPnl") BigDecimal minPnl,
            @Param("maxPnl") BigDecimal maxPnl,
            @Param("pnlPositive") boolean pnlPositive,
            @Param("pnlNegative") boolean pnlNegative,
            @Param("isEmotionalTrade") Boolean isEmotionalTrade,
            @Param("isUnplannedEntry") Boolean isUnplannedEntry,
            @Param("hasReview") Boolean hasReview,
            Pageable pageable);
}