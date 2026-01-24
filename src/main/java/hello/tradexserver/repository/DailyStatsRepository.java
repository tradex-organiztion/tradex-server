package hello.tradexserver.repository;

import hello.tradexserver.domain.DailyStats;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {
    // 특정 날짜 데이터 조회
    Optional<DailyStats> findByUserIdAndStatDate(Long userId, LocalDate statDate);

    // 특정 기간 데이터 조회
    @Query("SELECT d FROM DailyStats d " +
            "WHERE d.user.id = :userId " +
            "AND d.statDate BETWEEN :startDate AND :endDate " +
            "ORDER BY d.statDate ASC")
    List<DailyStats> findByUserIdAndStatDateBetween(@Param("userId") Long userId,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);
}
