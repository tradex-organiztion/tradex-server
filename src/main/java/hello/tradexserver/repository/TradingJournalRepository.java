package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingJournal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradingJournalRepository extends JpaRepository<TradingJournal, Long>,
        JpaSpecificationExecutor<TradingJournal> {

    Optional<TradingJournal> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT tj FROM TradingJournal tj
            JOIN FETCH tj.position p
            LEFT JOIN FETCH tj.refinedJournal rj
            WHERE tj.user.id = :userId
            AND (:symbol IS NULL OR p.symbol = :symbol)
            AND (:startDate IS NULL OR p.exitTime >= :startDate)
            AND p.status = 'CLOSED'
            ORDER BY p.exitTime DESC
            """)
    List<TradingJournal> searchJournals(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable);
}