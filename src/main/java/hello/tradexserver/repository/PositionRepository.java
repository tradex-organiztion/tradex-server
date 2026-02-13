package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Page<Position> findByUserId(Long userId, Pageable pageable);

    // 종료된 포지션 목록 조회 (상태만 필터링)
    Page<Position> findByUserIdAndStatus(Long userId, PositionStatus status, Pageable pageable);

    // 종료된 포지션 목록 조회 (상태 + 심볼 필터링)
    Page<Position> findByUserIdAndStatusAndSymbol(Long userId, PositionStatus status, String symbol, Pageable pageable);

    // 종료된 포지션 목록 조회 (상태 + 방향 필터링)
    Page<Position> findByUserIdAndStatusAndSide(Long userId, PositionStatus status, PositionSide side, Pageable pageable);

    // 종료된 포지션 목록 조회 (상태 + 심볼 + 방향 필터링)
    Page<Position> findByUserIdAndStatusAndSymbolAndSide(Long userId, PositionStatus status, String symbol, PositionSide side, Pageable pageable);

    // 기간 내 종료된 포지션 목록 조회 (시계열 차트용)
    @Query("SELECT p FROM Position p WHERE p.user.id = :userId " +
            "AND p.status = :status " +
            "AND p.exitTime >= :startDate " +
            "ORDER BY p.exitTime ASC")
    List<Position> findClosedPositionsByPeriod(
            @Param("userId") Long userId,
            @Param("status") PositionStatus status,
            @Param("startDate") LocalDateTime startDate);

    // 선물 거래 요약 통계 (총 손익, 승/패 횟수)
    @Query(value = "SELECT COALESCE(SUM(realized_pnl), 0) as total_pnl, " +
            "SUM(CASE WHEN realized_pnl > 0 THEN 1 ELSE 0 END) as win_count, " +
            "SUM(CASE WHEN realized_pnl <= 0 THEN 1 ELSE 0 END) as loss_count, " +
            "COUNT(*) as total_count " +
            "FROM positions WHERE user_id = :userId " +
            "AND status = :status " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR exit_time >= :startDate)",
            nativeQuery = true)
    Object[] getFuturesSummaryStats(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate);

    // 거래 규모 합계 (entryPrice * closedSize * leverage)
    @Query(value = "SELECT COALESCE(SUM(avg_entry_price * closed_size * COALESCE(leverage, 1)), 0) " +
            "FROM positions WHERE user_id = :userId " +
            "AND status = :status " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR exit_time >= :startDate)",
            nativeQuery = true)
    BigDecimal getTotalVolume(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate);

    // 페어별 손익 랭킹
    @Query(value = "SELECT symbol, " +
            "SUM(realized_pnl) as total_pnl, " +
            "COUNT(*) as trade_count, " +
            "SUM(CASE WHEN realized_pnl > 0 THEN 1 ELSE 0 END) as win_count " +
            "FROM positions WHERE user_id = :userId " +
            "AND status = :status " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR exit_time >= :startDate) " +
            "GROUP BY symbol " +
            "ORDER BY SUM(realized_pnl) DESC",
            nativeQuery = true)
    List<Object[]> getProfitRankingBySymbol(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate);

    // 종료 포지션 요약 (롱/숏별 손익)
    @Query(value = "SELECT COUNT(*) as total_count, " +
            "SUM(CASE WHEN realized_pnl > 0 THEN 1 ELSE 0 END) as win_count, " +
            "COALESCE(SUM(CASE WHEN side = 'LONG' THEN realized_pnl ELSE 0 END), 0) as long_pnl, " +
            "SUM(CASE WHEN side = 'LONG' THEN 1 ELSE 0 END) as long_count, " +
            "COALESCE(SUM(CASE WHEN side = 'SHORT' THEN realized_pnl ELSE 0 END), 0) as short_pnl, " +
            "SUM(CASE WHEN side = 'SHORT' THEN 1 ELSE 0 END) as short_count " +
            "FROM positions WHERE user_id = :userId " +
            "AND status = :status " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR exit_time >= :startDate)",
            nativeQuery = true)
    Object[] getClosedPositionsSummary(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate);

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