package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface PositionRepository extends PagingAndSortingRepository<Position, Long> {

    Page<Position> findByUserId(Long userId, Pageable pageable);
}
