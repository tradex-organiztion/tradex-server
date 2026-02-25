package hello.tradexserver.repository;

import hello.tradexserver.domain.ChartLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartLayoutRepository extends JpaRepository<ChartLayout, Long> {

    List<ChartLayout> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ChartLayout> findByIdAndUserId(Long id, Long userId);
}
