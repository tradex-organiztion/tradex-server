package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.ExchangeName;
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

        // 테스트 데이터 생성 — BYBIT 3건, BINANCE 2건, OPEN 1건
        createPosition("BTCUSDT", PositionSide.LONG, new BigDecimal("100"), PositionStatus.CLOSED, ExchangeName.BYBIT);
        createPosition("BTCUSDT", PositionSide.SHORT, new BigDecimal("-50"), PositionStatus.CLOSED, ExchangeName.BYBIT);
        createPosition("ETHUSDT", PositionSide.LONG, new BigDecimal("200"), PositionStatus.CLOSED, ExchangeName.BYBIT);
        createPosition("ETHUSDT", PositionSide.SHORT, new BigDecimal("80"), PositionStatus.CLOSED, ExchangeName.BINANCE);
        createPosition("SOLUSDT", PositionSide.LONG, new BigDecimal("-30"), PositionStatus.CLOSED, ExchangeName.BINANCE);
        createPosition("BTCUSDT", PositionSide.LONG, null, PositionStatus.OPEN, ExchangeName.BYBIT);

        em.flush();
        em.clear();
    }

    private void createPosition(String symbol, PositionSide side, BigDecimal pnl,
                                PositionStatus status, ExchangeName exchangeName) {
        Position position = Position.builder()
                .user(user)
                .symbol(symbol)
                .side(side)
                .exchangeName(exchangeName)
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
    @DisplayName("거래소별 필터링 조회")
    class FindByExchangeName {

        @Test
        @DisplayName("거래소별 종료 포지션 조회 - BYBIT")
        void 거래소별_종료_포지션_조회_BYBIT() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndExchangeName(
                    user.getId(), PositionStatus.CLOSED, ExchangeName.BYBIT, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).allMatch(p -> p.getExchangeName() == ExchangeName.BYBIT);
        }

        @Test
        @DisplayName("거래소별 종료 포지션 조회 - BINANCE")
        void 거래소별_종료_포지션_조회_BINANCE() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndExchangeName(
                    user.getId(), PositionStatus.CLOSED, ExchangeName.BINANCE, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).allMatch(p -> p.getExchangeName() == ExchangeName.BINANCE);
        }

        @Test
        @DisplayName("거래소 + 심볼 필터링 조회")
        void 거래소_심볼_필터링_조회() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndExchangeNameAndSymbol(
                    user.getId(), PositionStatus.CLOSED, ExchangeName.BYBIT, "BTCUSDT", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).allMatch(p ->
                    p.getExchangeName() == ExchangeName.BYBIT && p.getSymbol().equals("BTCUSDT"));
        }

        @Test
        @DisplayName("거래소 + 방향 필터링 조회")
        void 거래소_방향_필터링_조회() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndExchangeNameAndSide(
                    user.getId(), PositionStatus.CLOSED, ExchangeName.BYBIT, PositionSide.LONG, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).allMatch(p ->
                    p.getExchangeName() == ExchangeName.BYBIT && p.getSide() == PositionSide.LONG);
        }

        @Test
        @DisplayName("거래소 + 심볼 + 방향 필터링 조회")
        void 거래소_심볼_방향_필터링_조회() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndExchangeNameAndSymbolAndSide(
                    user.getId(), PositionStatus.CLOSED, ExchangeName.BYBIT, "BTCUSDT", PositionSide.LONG,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            Position p = result.getContent().get(0);
            assertThat(p.getExchangeName()).isEqualTo(ExchangeName.BYBIT);
            assertThat(p.getSymbol()).isEqualTo("BTCUSDT");
            assertThat(p.getSide()).isEqualTo(PositionSide.LONG);
        }

        @Test
        @DisplayName("존재하지 않는 거래소 조회 시 빈 결과")
        void 존재하지_않는_거래소_조회() {
            Page<Position> result = positionRepository.findByUserIdAndStatusAndExchangeName(
                    user.getId(), PositionStatus.CLOSED, ExchangeName.BITGET, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Native Query 집계 테스트")
    class NativeQueryTest {

        @Test
        @DisplayName("거래 규모 합계 조회 - 전체 거래소")
        void 거래_규모_합계_조회_전체() {
            BigDecimal result = positionRepository.getTotalVolume(user.getId(), "CLOSED", null, null);

            assertThat(result).isNotNull();
            // 40000 * 0.1 * 10 = 40000 per position, 5 closed positions = 200000
            assertThat(result).isEqualByComparingTo(new BigDecimal("200000"));
        }

        @Test
        @DisplayName("거래 규모 합계 조회 - BYBIT만")
        void 거래_규모_합계_조회_BYBIT() {
            BigDecimal result = positionRepository.getTotalVolume(user.getId(), "CLOSED", null, "BYBIT");

            assertThat(result).isNotNull();
            // BYBIT 3건: 40000 * 0.1 * 10 * 3 = 120000
            assertThat(result).isEqualByComparingTo(new BigDecimal("120000"));
        }

        @Test
        @DisplayName("선물 요약 통계 조회 - 전체 거래소")
        void 선물_요약_통계_조회_전체() {
            Object[] result = positionRepository.getFuturesSummaryStats(user.getId(), "CLOSED", null, null);

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;
            assertThat(row.length).isGreaterThanOrEqualTo(4);

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
        @DisplayName("선물 요약 통계 조회 - BYBIT만")
        void 선물_요약_통계_조회_BYBIT() {
            Object[] result = positionRepository.getFuturesSummaryStats(user.getId(), "CLOSED", null, "BYBIT");

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;

            BigDecimal totalPnl = new BigDecimal(row[0].toString());
            int winCount = ((Number) row[1]).intValue();
            int lossCount = ((Number) row[2]).intValue();
            int totalCount = ((Number) row[3]).intValue();

            // BYBIT: 100 + (-50) + 200 = 250
            assertThat(totalPnl).isEqualByComparingTo(new BigDecimal("250"));
            assertThat(winCount).isEqualTo(2);  // 100, 200
            assertThat(lossCount).isEqualTo(1); // -50
            assertThat(totalCount).isEqualTo(3);
        }

        @Test
        @DisplayName("선물 요약 통계 조회 - BINANCE만")
        void 선물_요약_통계_조회_BINANCE() {
            Object[] result = positionRepository.getFuturesSummaryStats(user.getId(), "CLOSED", null, "BINANCE");

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;

            BigDecimal totalPnl = new BigDecimal(row[0].toString());
            int winCount = ((Number) row[1]).intValue();
            int lossCount = ((Number) row[2]).intValue();
            int totalCount = ((Number) row[3]).intValue();

            // BINANCE: 80 + (-30) = 50
            assertThat(totalPnl).isEqualByComparingTo(new BigDecimal("50"));
            assertThat(winCount).isEqualTo(1);  // 80
            assertThat(lossCount).isEqualTo(1); // -30
            assertThat(totalCount).isEqualTo(2);
        }

        @Test
        @DisplayName("페어별 랭킹 조회 - 전체 거래소")
        void 페어별_랭킹_조회_전체() {
            List<Object[]> result = positionRepository.getProfitRankingBySymbol(user.getId(), "CLOSED", null, null);

            assertThat(result).hasSize(3); // BTCUSDT, ETHUSDT, SOLUSDT
        }

        @Test
        @DisplayName("페어별 랭킹 조회 - BYBIT만")
        void 페어별_랭킹_조회_BYBIT() {
            List<Object[]> result = positionRepository.getProfitRankingBySymbol(user.getId(), "CLOSED", null, "BYBIT");

            assertThat(result).hasSize(2); // BTCUSDT, ETHUSDT (BYBIT에만 존재)
        }

        @Test
        @DisplayName("종료 포지션 요약 조회 - 전체 거래소")
        void 종료_포지션_요약_조회_전체() {
            Object[] result = positionRepository.getClosedPositionsSummary(user.getId(), "CLOSED", null, null);

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;
            assertThat(row.length).isGreaterThanOrEqualTo(6);

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

        @Test
        @DisplayName("종료 포지션 요약 조회 - BYBIT만")
        void 종료_포지션_요약_조회_BYBIT() {
            Object[] result = positionRepository.getClosedPositionsSummary(user.getId(), "CLOSED", null, "BYBIT");

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;

            int totalCount = ((Number) row[0]).intValue();
            BigDecimal longPnl = new BigDecimal(row[2].toString());
            int longCount = ((Number) row[3]).intValue();
            BigDecimal shortPnl = new BigDecimal(row[4].toString());
            int shortCount = ((Number) row[5]).intValue();

            assertThat(totalCount).isEqualTo(3);
            // BYBIT LONG: 100 + 200 = 300
            assertThat(longPnl).isEqualByComparingTo(new BigDecimal("300"));
            assertThat(longCount).isEqualTo(2);
            // BYBIT SHORT: -50
            assertThat(shortPnl).isEqualByComparingTo(new BigDecimal("-50"));
            assertThat(shortCount).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 거래소 통계 조회 시 0 반환")
        void 존재하지_않는_거래소_통계_조회() {
            Object[] result = positionRepository.getFuturesSummaryStats(user.getId(), "CLOSED", null, "BITGET");

            assertThat(result).isNotNull();
            Object[] row = result[0] instanceof Object[] ? (Object[]) result[0] : result;

            BigDecimal totalPnl = new BigDecimal(row[0].toString());
            int totalCount = ((Number) row[3]).intValue();

            assertThat(totalPnl).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(totalCount).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findClosedPositionsByPeriod - 기간별 조회")
    class FindClosedPositionsByPeriod {

        @Test
        @DisplayName("전체 거래소 기간별 조회")
        void 전체_거래소_기간별_조회() {
            List<Position> result = positionRepository.findClosedPositionsByPeriod(
                    user.getId(), PositionStatus.CLOSED, LocalDateTime.now().minusDays(7), null);

            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("BYBIT만 기간별 조회")
        void BYBIT_기간별_조회() {
            List<Position> result = positionRepository.findClosedPositionsByPeriod(
                    user.getId(), PositionStatus.CLOSED, LocalDateTime.now().minusDays(7), ExchangeName.BYBIT);

            assertThat(result).hasSize(3);
            assertThat(result).allMatch(p -> p.getExchangeName() == ExchangeName.BYBIT);
        }

        @Test
        @DisplayName("BINANCE만 기간별 조회")
        void BINANCE_기간별_조회() {
            List<Position> result = positionRepository.findClosedPositionsByPeriod(
                    user.getId(), PositionStatus.CLOSED, LocalDateTime.now().minusDays(7), ExchangeName.BINANCE);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> p.getExchangeName() == ExchangeName.BINANCE);
        }
    }
}
