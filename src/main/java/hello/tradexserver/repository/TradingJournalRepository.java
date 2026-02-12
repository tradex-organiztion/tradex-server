package hello.tradexserver.repository;

import hello.tradexserver.domain.TradingJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradingJournalRepository extends JpaRepository<TradingJournal, Long> {

    Page<TradingJournal> findByUserId(Long userId, Pageable pageable);

    Optional<TradingJournal> findByIdAndUserId(Long id, Long userId);
}