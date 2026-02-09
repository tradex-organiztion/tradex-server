package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Page<Position> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT p FROM Position p WHERE p.user.id = :userId " +
           "AND p.exchangeName = :exchangeName AND p.symbol = :symbol " +
           "AND p.side = :side AND p.status = 'OPEN'")
    Optional<Position> findOpenPosition(Long userId, ExchangeName exchangeName,
                                         String symbol, PositionSide side);

    List<Position> findByUserIdAndStatus(Long userId, PositionStatus status);
}