package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingPrinciple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradingPrincipleRepository extends JpaRepository<TradingPrinciple, Long> {

    List<TradingPrinciple> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TradingPrinciple> findByIdAndUserId(Long id, Long userId);
}
