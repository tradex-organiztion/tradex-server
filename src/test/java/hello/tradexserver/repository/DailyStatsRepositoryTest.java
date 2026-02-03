package hello.tradexserver.repository;

import hello.tradexserver.domain.DailyStats;
import hello.tradexserver.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyStatsRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test-dailystats@test.com")
                .username("testuser")
                .build();
        em.persist(user);

        createDailyStat(LocalDate.of(2024, 1, 1), new BigDecimal("100"), 2, 1, new BigDecimal("10000"));
        createDailyStat(LocalDate.of(2024, 1, 2), new BigDecimal("150"), 3, 0, new BigDecimal("10150"));
        createDailyStat(LocalDate.of(2024, 1, 3), new BigDecimal("-50"), 1, 2, new BigDecimal("10100"));
        createDailyStat(LocalDate.of(2024, 1, 15), new BigDecimal("200"), 4, 1, new BigDecimal("10300"));
        createDailyStat(LocalDate.of(2024, 1, 31), new BigDecimal("300"), 5, 0, new BigDecimal("10600"));
        createDailyStat(LocalDate.of(2024, 2, 1), new BigDecimal("50"), 1, 1, new BigDecimal("10650"));

        em.flush();
        em.clear();
    }

    private void createDailyStat(LocalDate date, BigDecimal pnl, int winCount, int lossCount, BigDecimal totalAsset) {
        DailyStats stat = DailyStats.builder()
                .user(user)
                .statDate(date)
                .realizedPnl(pnl)
                .winCount(winCount)
                .lossCount(lossCount)
                .totalAsset(totalAsset)
                .build();
        em.persist(stat);
    }

    @Nested
    @DisplayName("월별 데이터 조회")
    class FindByYearMonth {

        @Test
        @DisplayName("2024년 1월 데이터 조회")
        void 월별_데이터_조회() {
            List<DailyStats> result = dailyStatsRepository.findByUserIdAndYearMonth(user.getId(), 2024, 1);

            assertThat(result).hasSize(5);
            assertThat(result.get(0).getStatDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(result.get(4).getStatDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        }
    }

    @Nested
    @DisplayName("월초/월말 데이터")
    class FindFirstLastOfMonth {

        @Test
        @DisplayName("월초 데이터 조회")
        void 월초_데이터_조회() {
            Optional<DailyStats> result = dailyStatsRepository.findFirstOfMonth(user.getId(), 2024, 1);

            assertThat(result).isPresent();
            assertThat(result.get().getStatDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        }

        @Test
        @DisplayName("월말 데이터 조회")
        void 월말_데이터_조회() {
            Optional<DailyStats> result = dailyStatsRepository.findLastOfMonth(user.getId(), 2024, 1);

            assertThat(result).isPresent();
            assertThat(result.get().getStatDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        }
    }

    @Nested
    @DisplayName("기간별 조회")
    class FindByDateRange {

        @Test
        @DisplayName("기간 내 데이터 조회")
        void 기간별_데이터_조회() {
            List<DailyStats> result = dailyStatsRepository.findByUserIdAndStatDateBetween(
                    user.getId(), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 15));

            assertThat(result).hasSize(4);
        }

        @Test
        @DisplayName("주간 손익 합계 조회")
        void 주간_손익_합계_조회() {
            BigDecimal result = dailyStatsRepository.getWeeklyPnlSum(
                    user.getId(), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3));

            assertThat(result).isEqualByComparingTo(new BigDecimal("200"));
        }
    }

    @Nested
    @DisplayName("월별 통계")
    class MonthlyStats {

        @Test
        @DisplayName("월별 합계 통계 조회 - 결과 존재 확인")
        void 월별_합계_통계_조회() {
            Object[] result = dailyStatsRepository.getMonthlyStats(user.getId(), 2024, 1);

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;
            assertThat(row.length).isEqualTo(3);

            BigDecimal totalPnl = (BigDecimal) row[0];
            Long winCount = (Long) row[1];
            Long lossCount = (Long) row[2];

            // 100 + 150 + (-50) + 200 + 300 = 700
            assertThat(totalPnl).isEqualByComparingTo(new BigDecimal("700"));
            // 2 + 3 + 1 + 4 + 5 = 15
            assertThat(winCount).isEqualTo(15L);
            // 1 + 0 + 2 + 1 + 0 = 4
            assertThat(lossCount).isEqualTo(4L);
        }
    }
}
