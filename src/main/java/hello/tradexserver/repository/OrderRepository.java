package hello.tradexserver.repository;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.OrderSide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByPositionId(Long positionId);

    Optional<Order> findByExchangeOrderId(String exchangeOrderId);

    boolean existsByExchangeOrderId(String exchangeOrderId);

    /**
     * 중복 저장 방지: 이미 DB에 있는 exchangeOrderId 목록 반환
     */
    @Query("""
        SELECT o.exchangeOrderId FROM Order o
        WHERE o.exchangeName = :exchangeName
          AND o.exchangeOrderId IN :orderIds
        """)
    Set<String> findExistingOrderIds(
            @Param("exchangeName") ExchangeName exchangeName,
            @Param("orderIds") List<String> orderIds
    );

    /**
     * 신규 포지션 entryTime 설정용: 가장 최근 진입 오더의 fillTime 조회
     * orderSide: 헷지 모드에서 LONG 포지션 → BUY 오더, SHORT 포지션 → SELL 오더
     */
    @Query("""
        SELECT o.fillTime FROM Order o
        WHERE o.exchangeApiKey.id = :apiKeyId
          AND o.symbol = :symbol
          AND o.side = :orderSide
          AND o.positionEffect = 'OPEN'
          AND o.fillTime IS NOT NULL
        ORDER BY o.fillTime DESC
        LIMIT 1
        """)
    Optional<LocalDateTime> findLatestOpenOrderFillTime(
            @Param("apiKeyId") Long apiKeyId,
            @Param("symbol") String symbol,
            @Param("orderSide") OrderSide orderSide
    );

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /**
     * Position 매핑용: apiKey + symbol + 시간 범위로 Order 조회
     */
    @Query("""
        SELECT o FROM Order o
        WHERE o.user.id = :userId
          AND o.exchangeApiKey.id = :apiKeyId
          AND o.symbol = :symbol
          AND o.fillTime BETWEEN :startTime AND :endTime
        ORDER BY o.fillTime ASC
        """)
    List<Order> findOrdersForMapping(
            @Param("userId") Long userId,
            @Param("apiKeyId") Long apiKeyId,
            @Param("symbol") String symbol,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}