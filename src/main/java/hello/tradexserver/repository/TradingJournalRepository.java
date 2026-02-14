package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradingJournalRepository extends JpaRepository<TradingJournal, Long> {

    Page<TradingJournal> findByUserId(Long userId, Pageable pageable);

    @Query("""
        SELECT j FROM TradingJournal j JOIN j.position p
        WHERE j.user.id = :userId
          AND (:symbol IS NULL OR p.symbol = :symbol)
          AND (:side IS NULL OR p.side = :side)
          AND (:status IS NULL OR p.status = :status)
        """)
    Page<TradingJournal> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("side") PositionSide side,
            @Param("status") PositionStatus status,
            Pageable pageable
    );

    Optional<TradingJournal> findByIdAndUserId(Long id, Long userId);
}