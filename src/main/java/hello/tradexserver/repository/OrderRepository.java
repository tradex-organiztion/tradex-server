package hello.tradexserver.repository;

import hello.tradexserver.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByPositionId(Long positionId);

    Optional<Order> findByExchangeOrderId(String exchangeOrderId);

    boolean existsByExchangeOrderId(String exchangeOrderId);
}