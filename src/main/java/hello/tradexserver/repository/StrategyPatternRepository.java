package hello.tradexserver.repository;

import hello.tradexserver.domain.StrategyPattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StrategyPatternRepository extends JpaRepository<StrategyPattern, Long> {

    List<StrategyPattern> findTop3ByUserIdOrderByWinRateDesc(Long userId);
}
