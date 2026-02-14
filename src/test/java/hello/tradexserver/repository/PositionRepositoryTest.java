package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled
class PositionRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PositionRepository positionRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test-position@test.com")
                .username("testuser")
                .build();
        em.persist(user);

        // 테스트 데이터 생성
        createPosition("BTCUSDT", PositionSide.LONG, new BigDecimal("100"), PositionStatus.CLOSED);
        createPosition("BTCUSDT", PositionSide.SHORT, new BigDecimal("-50"), PositionStatus.CLOSED);
        createPosition("ETHUSDT", PositionSide.LONG, new BigDecimal("200"), PositionStatus.CLOSED);
        createPosition("ETHUSDT", PositionSide.SHORT, new BigDecimal("80"), PositionStatus.CLOSED);
        createPosition("SOLUSDT", PositionSide.LONG, new BigDecimal("-30"), PositionStatus.CLOSED);
        createPosition("BTCUSDT", PositionSide.LONG, null, PositionStatus.OPEN);

        em.flush();
        em.clear();
    }

    private void createPosition(String symbol, PositionSide side, BigDecimal pnl, PositionStatus status) {
        Position position = Position.builder()
                .user(user)
                .symbol(symbol)
                .side(side)
                .avgEntryPrice(new BigDecimal("40000"))
                .closedSize(new BigDecimal("0.1"))
                .leverage(10)
                .entryTime(LocalDateTime.now().minusDays(1))
                .exitTime(status == PositionStatus.CLOSED ? LocalDateTime.now() : null)
                .avgExitPrice(status == PositionStatus.CLOSED ? new BigDecimal("41000") : null)
                .realizedPnl(pnl)
                .openFee(new BigDecimal("2.5"))
                .closedFee(new BigDecimal("2.5"))
                .status(status)
                .build();
        em.persist(position);
    }

    @Nested
    @DisplayName("findByUserIdAndStatus - 종료 포지션 조회")
    class FindByUserIdAndStatus {

        @Test
        @DisplayName("모든 종료 포지션 조회")
        void 모든_종료_포지션_조회() {
            Page<Position> result = positionRepository.findByUserIdAndStatus(
                    user.getId(), PositionStatus.CLOSED, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("심볼 필터링 조회")
        void 심볼_필터링_조회() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndSymbol(
                    user.getId(), PositionStatus.CLOSED, "BTCUSDT", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).allMatch(p -> p.getSymbol().equals("BTCUSDT"));
        }

        @Test
        @DisplayName("포지션 방향 필터링 조회")
        void 포지션_방향_필터링_조회() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndSide(
                    user.getId(), PositionStatus.CLOSED, PositionSide.LONG, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).allMatch(p -> p.getSide() == PositionSide.LONG);
        }
    }

    @Nested
    @DisplayName("Native Query 집계 테스트")
    class NativeQueryTest {

        @Test
        @DisplayName("거래 규모 합계 조회")
        void 거래_규모_합계_조회() {
            BigDecimal result = positionRepository.getTotalVolume(user.getId(), "CLOSED", null);

            // 결과가 null이 아닌지 확인
            assertThat(result).isNotNull();
            // 40000 * 0.1 * 10 = 40000 per position, 5 closed positions = 200000
            assertThat(result).isEqualByComparingTo(new BigDecimal("200000"));
        }

        @Test
        @DisplayName("선물 요약 통계 조회 - 결과 존재 확인")
        void 선물_요약_통계_조회() {
            Object[] result = positionRepository.getFuturesSummaryStats(user.getId(), "CLOSED", null);

            assertThat(result).isNotNull();
            // Native Query는 Object[] 안에 Object[]로 반환
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;
            assertThat(row.length).isGreaterThanOrEqualTo(4);

            // totalPnl, winCount, lossCount, totalCount
            BigDecimal totalPnl = new BigDecimal(row[0].toString());
            int winCount = ((Number) row[1]).intValue();
            int lossCount = ((Number) row[2]).intValue();
            int totalCount = ((Number) row[3]).intValue();

            // 100 + (-50) + 200 + 80 + (-30) = 300
            assertThat(totalPnl).isEqualByComparingTo(new BigDecimal("300"));
            assertThat(winCount).isEqualTo(3);  // 100, 200, 80
            assertThat(lossCount).isEqualTo(2); // -50, -30
            assertThat(totalCount).isEqualTo(5);
        }

        @Test
        @DisplayName("페어별 랭킹 조회")
        void 페어별_랭킹_조회() {
            List<Object[]> result = positionRepository.getProfitRankingBySymbol(user.getId(), "CLOSED", null);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("종료 포지션 요약 조회 - 결과 존재 확인")
        void 종료_포지션_요약_조회() {
            Object[] result = positionRepository.getClosedPositionsSummary(user.getId(), "CLOSED", null);

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;
            assertThat(row.length).isGreaterThanOrEqualTo(6);

            // totalCount, winCount, longPnl, longCount, shortPnl, shortCount
            int totalCount = ((Number) row[0]).intValue();
            int winCount = ((Number) row[1]).intValue();
            BigDecimal longPnl = new BigDecimal(row[2].toString());
            int longCount = ((Number) row[3]).intValue();
            BigDecimal shortPnl = new BigDecimal(row[4].toString());
            int shortCount = ((Number) row[5]).intValue();

            assertThat(totalCount).isEqualTo(5);
            assertThat(winCount).isEqualTo(3);
            // LONG: 100 + 200 + (-30) = 270
            assertThat(longPnl).isEqualByComparingTo(new BigDecimal("270"));
            assertThat(longCount).isEqualTo(3);
            // SHORT: (-50) + 80 = 30
            assertThat(shortPnl).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(shortCount).isEqualTo(2);
        }
    }
}
