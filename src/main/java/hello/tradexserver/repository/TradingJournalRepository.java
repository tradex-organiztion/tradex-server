package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradingJournalRepository extends JpaRepository<TradingJournal, Long>,
        JpaSpecificationExecutor<TradingJournal> {

    Optional<TradingJournal> findByIdAndUserId(Long id, Long userId);
}