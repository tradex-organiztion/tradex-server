package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Page<Position> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT p FROM Position p WHERE p.user.id = :userId " +
           "AND p.exchangeName = :exchangeName AND p.symbol = :symbol " +
           "AND p.side = :side AND p.status = 'OPEN'")
    Optional<Position> findOpenPosition(Long userId, ExchangeName exchangeName,
                                         String symbol, PositionSide side);

    List<Position> findByUserIdAndStatus(Long userId, PositionStatus status);

    List<Position> findByStatus(PositionStatus status);

    /**
     * exchangeApiKeyId + symbol + side + OPEN 상태로 조회 (WebSocket 업데이트용)
     */
    @Query("""
        SELECT p FROM Position p
        WHERE p.exchangeApiKey.id = :apiKeyId
          AND p.symbol = :symbol
          AND p.side = :side
          AND p.status = 'OPEN'
        """)
    Optional<Position> findOpenPositionByApiKey(
            @Param("apiKeyId") Long apiKeyId,
            @Param("symbol") String symbol,
            @Param("side") PositionSide side
    );

    /**
     * 단방향 모드에서 side 없이 symbol만으로 OPEN 포지션 조회
     */
    @Query("""
        SELECT p FROM Position p
        WHERE p.exchangeApiKey.id = :apiKeyId
          AND p.symbol = :symbol
          AND p.status = 'OPEN'
        """)
    Optional<Position> findOpenPositionByApiKeyAndSymbol(
            @Param("apiKeyId") Long apiKeyId,
            @Param("symbol") String symbol
    );

    /**
     * 특정 apiKey의 모든 OPEN 포지션 조회 (재연결 시 Gap 보완용)
     */
    @Query("""
        SELECT p FROM Position p
        WHERE p.exchangeApiKey.id = :apiKeyId
          AND p.status = 'OPEN'
        """)
    List<Position> findAllOpenByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    Optional<Position> findByIdAndUserId(Long id, Long userId);
}