package hello.tradexserver.repository;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.enums.ExchangeName;
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

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /**
     * 오더 기반 포지션 재구성용: apiKey의 미매핑 오더 전체 조회 (fillTime 순)
     */
    @Query("""
        SELECT o FROM Order o
        WHERE o.exchangeApiKey.id = :apiKeyId
          AND o.position IS NULL
          AND o.filledQuantity > 0
        ORDER BY o.fillTime ASC
        """)
    List<Order> findUnmappedOrdersByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 서버 재시작/첫 연결 시 gap 시작 시점 결정용: apiKey의 마지막 오더 fillTime 조회
     */
    @Query("SELECT MAX(o.fillTime) FROM Order o WHERE o.exchangeApiKey.id = :apiKeyId")
    Optional<LocalDateTime> findLastFillTimeByApiKeyId(@Param("apiKeyId") Long apiKeyId);

}