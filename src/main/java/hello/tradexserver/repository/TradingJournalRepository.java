package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingJournal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
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

    @Override
    @EntityGraph(attributePaths = {"position", "refinedJournal"})
    List<TradingJournal> findAll(Specification<TradingJournal> spec,
                                Sort sort);
}