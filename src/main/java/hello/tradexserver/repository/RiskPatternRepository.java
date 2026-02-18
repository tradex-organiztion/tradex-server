package hello.tradexserver.repository;

import hello.tradexserver.domain.RiskPattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskPatternRepository extends JpaRepository<RiskPattern, Long> {

    Optional<RiskPattern> findByUserIdAndExchangeNameIsNull(Long userId);
}
