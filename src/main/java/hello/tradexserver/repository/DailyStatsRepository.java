package hello.tradexserver.repository;

import hello.tradexserver.domain.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    List<DailyStats> findByUserIdAndStatDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 월별 데이터 조회 (년, 월 기준)
    @Query("SELECT d FROM DailyStats d " +
            "WHERE d.user.id = :userId " +
            "AND YEAR(d.statDate) = :year " +
            "AND MONTH(d.statDate) = :month " +
            "ORDER BY d.statDate ASC")
    List<DailyStats> findByUserIdAndYearMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

    // 월별 합계 통계 조회
    @Query("SELECT COALESCE(SUM(d.realizedPnl), 0), " +
            "COALESCE(SUM(d.winCount), 0), " +
            "COALESCE(SUM(d.lossCount), 0) " +
            "FROM DailyStats d " +
            "WHERE d.user.id = :userId " +
            "AND YEAR(d.statDate) = :year " +
            "AND MONTH(d.statDate) = :month")
    Object[] getMonthlyStats(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

    // 해당 월의 첫 번째 데이터 조회 (월초 자산 확인용)
    @Query("SELECT d FROM DailyStats d " +
            "WHERE d.user.id = :userId " +
            "AND YEAR(d.statDate) = :year " +
            "AND MONTH(d.statDate) = :month " +
            "ORDER BY d.statDate ASC " +
            "LIMIT 1")
    Optional<DailyStats> findFirstOfMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

    // 해당 월의 마지막 데이터 조회 (월말 자산 확인용)
    @Query("SELECT d FROM DailyStats d " +
            "WHERE d.user.id = :userId " +
            "AND YEAR(d.statDate) = :year " +
            "AND MONTH(d.statDate) = :month " +
            "ORDER BY d.statDate DESC " +
            "LIMIT 1")
    Optional<DailyStats> findLastOfMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

    // 주간 합계 통계 조회 (최근 7일)
    @Query("SELECT COALESCE(SUM(d.realizedPnl), 0) " +
            "FROM DailyStats d " +
            "WHERE d.user.id = :userId " +
            "AND d.statDate BETWEEN :startDate AND :endDate")
    java.math.BigDecimal getWeeklyPnlSum(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
