package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingPrincipleCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradingPrincipleCheckRepository extends JpaRepository<TradingPrincipleCheck, Long> {

    List<TradingPrincipleCheck> findByTradingJournalId(Long tradingJournalId);

    void deleteByTradingJournalId(Long tradingJournalId);
}