package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.dto.request.JournalStatsFilterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TradingJournalRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TradingJournalRepository tradingJournalRepository;

    private User user;
    private final LocalDateTime now = LocalDateTime.now();

    /**
     * 테스트 데이터:
     * - trade1: RSI+MACD | 1H | 지지저항 | LONG | +200 | UPTREND | SCALPING(2h)
     * - trade2: 볼린저밴드 | 4H | 추세선 | SHORT | -50 | DOWNTREND | SWING(3days)
     * - trade3: RSI | 1H+4H | 지지저항 | LONG | +100 | UPTREND | SCALPING(30min)
     * - trade4: RSI+볼린저밴드 | 1D | 지지저항+추세선 | LONG | +150 | SIDEWAYS | SWING(5days)
     * - trade5: RSI | (OPEN, 통계 제외)
     */
    @BeforeEach
    void setUp() {
        user = em.persist(User.builder()
                .email("journal-stats-test@test.com")
                .username("journalStatsTestUser")
                .build());

        // trade1: LONG, WIN(+200), UPTREND, SCALPING(2h)
        Position p1 = em.persist(Position.builder()
                .user(user).symbol("BTCUSDT").side(PositionSide.LONG)
                .marketCondition(MarketCondition.UPTREND)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).currentSize(BigDecimal.ZERO).leverage(10)
                .realizedPnl(new BigDecimal("200"))
                .entryTime(now.minusHours(2)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder().user(user).position(p1)
                .indicators(new ArrayList<>(List.of("RSI", "MACD")))
                .timeframes(new ArrayList<>(List.of("1H")))
                .technicalAnalyses(new ArrayList<>(List.of("지지저항")))
                .build());

        // trade2: SHORT, LOSS(-50), DOWNTREND, SWING(3days)
        Position p2 = em.persist(Position.builder()
                .user(user).symbol("BTCUSDT").side(PositionSide.SHORT)
                .marketCondition(MarketCondition.DOWNTREND)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).currentSize(BigDecimal.ZERO).leverage(10)
                .realizedPnl(new BigDecimal("-50"))
                .entryTime(now.minusDays(3)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder().user(user).position(p2)
                .indicators(new ArrayList<>(List.of("볼린저밴드")))
                .timeframes(new ArrayList<>(List.of("4H")))
                .technicalAnalyses(new ArrayList<>(List.of("추세선")))
                .build());

        // trade3: LONG, WIN(+100), UPTREND, SCALPING(30min)
        Position p3 = em.persist(Position.builder()
                .user(user).symbol("ETHUSDT").side(PositionSide.LONG)
                .marketCondition(MarketCondition.UPTREND)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).currentSize(BigDecimal.ZERO).leverage(10)
                .realizedPnl(new BigDecimal("100"))
                .entryTime(now.minusMinutes(30)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder().user(user).position(p3)
                .indicators(new ArrayList<>(List.of("RSI")))
                .timeframes(new ArrayList<>(List.of("1H", "4H")))
                .technicalAnalyses(new ArrayList<>(List.of("지지저항")))
                .build());

        // trade4: LONG, WIN(+150), SIDEWAYS, SWING(5days)
        Position p4 = em.persist(Position.builder()
                .user(user).symbol("ETHUSDT").side(PositionSide.LONG)
                .marketCondition(MarketCondition.SIDEWAYS)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).currentSize(BigDecimal.ZERO).leverage(10)
                .realizedPnl(new BigDecimal("150"))
                .entryTime(now.minusDays(5)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder().user(user).position(p4)
                .indicators(new ArrayList<>(List.of("RSI", "볼린저밴드")))
                .timeframes(new ArrayList<>(List.of("1D")))
                .technicalAnalyses(new ArrayList<>(List.of("지지저항", "추세선")))
                .build());

        // trade5: OPEN 포지션 - 통계에서 제외되어야 함
        Position openPos = em.persist(Position.builder()
                .user(user).symbol("BTCUSDT").side(PositionSide.LONG)
                .avgEntryPrice(new BigDecimal("40000")).currentSize(new BigDecimal("0.1"))
                .entryTime(now.minusHours(1))
                .status(PositionStatus.OPEN).build());
        em.persist(TradingJournal.builder().user(user).position(openPos)
                .indicators(new ArrayList<>(List.of("RSI")))
                .build());

        em.flush();
        em.clear();
    }

    // ─── findDistinct* 쿼리 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("findDistinct* - 고유값 목록 조회")
    class FindDistinctOptions {

        @Test
        @DisplayName("지표 고유값 조회 - 정렬된 3개 반환")
        void 지표_고유값_조회() {
            List<String> result = tradingJournalRepository.findDistinctIndicatorsByUser(user.getId());

            // RSI, MACD, 볼린저밴드 (알파벳/가나다 정렬)
            assertThat(result).hasSize(3);
            assertThat(result).contains("RSI", "MACD", "볼린저밴드");
        }

        @Test
        @DisplayName("타임프레임 고유값 조회 - 정렬된 3개 반환")
        void 타임프레임_고유값_조회() {
            List<String> result = tradingJournalRepository.findDistinctTimeframesByUser(user.getId());

            // 1D, 1H, 4H (ORDER BY 정렬)
            assertThat(result).hasSize(3);
            assertThat(result).contains("1D", "1H", "4H");
        }

        @Test
        @DisplayName("기술적분석 고유값 조회 - 정렬된 2개 반환")
        void 기술적분석_고유값_조회() {
            List<String> result = tradingJournalRepository.findDistinctTechnicalAnalysesByUser(user.getId());

            assertThat(result).hasSize(2);
            assertThat(result).contains("지지저항", "추세선");
        }

        @Test
        @DisplayName("다른 사용자의 데이터는 포함되지 않음")
        void 다른사용자_데이터_미포함() {
            User otherUser = em.persist(User.builder()
                    .email("other@test.com").username("otherUser").build());
            Position pos = em.persist(Position.builder()
                    .user(otherUser).symbol("BTCUSDT").side(PositionSide.LONG)
                    .avgEntryPrice(new BigDecimal("40000")).currentSize(BigDecimal.ZERO).entryTime(now.minusHours(1))
                    .status(PositionStatus.CLOSED).build());
            em.persist(TradingJournal.builder().user(otherUser).position(pos)
                    .indicators(new ArrayList<>(List.of("스토캐스틱"))).build());
            em.flush();
            em.clear();

            List<String> result = tradingJournalRepository.findDistinctIndicatorsByUser(user.getId());

            assertThat(result).doesNotContain("스토캐스틱");
        }
    }

    // ─── getJournalStats 쿼리 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getJournalStats - 필터 조합 통계")
    class GetJournalStats {

        @Test
        @DisplayName("필터 없음 - 전체 CLOSED 포지션 통계 (4건)")
        void 필터없음_전체통계() {
            // trade1(+200) + trade2(-50) + trade3(+100) + trade4(+150) = 4건, 승3 패1
            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), emptyFilter());

            assertThat(((Number) row[0]).intValue()).isEqualTo(4);           // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(3);           // winCount
            assertThat(((Number) row[2]).intValue()).isEqualTo(1);           // lossCount
            // avgPnl = (200 - 50 + 100 + 150) / 4 = 100
            assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("100"));
            // avgRoi = (50 - 12.5 + 25 + 37.5) / 4 = 25
            assertThat((BigDecimal) row[4]).isEqualByComparingTo(new BigDecimal("25"));
        }

        @Test
        @DisplayName("OPEN 포지션 제외 - 통계에 미포함")
        void OPEN_포지션_제외() {
            // OPEN인 trade5가 포함되면 totalTrades=5 이어야 하지만 4여야 함
            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), emptyFilter());

            assertThat(((Number) row[0]).intValue()).isEqualTo(4);
        }

        @Test
        @DisplayName("indicator 단일 필터 - RSI 포함 트레이드 (1,3,4)")
        void indicator_RSI_필터() {
            // trade1(RSI,MACD), trade3(RSI), trade4(RSI,볼린저밴드) → 3건, 모두 WIN
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    List.of("RSI"), null, null, null, null, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(3);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(3);  // winCount
            assertThat(((Number) row[2]).intValue()).isEqualTo(0);  // lossCount
            // avgPnl = (200 + 100 + 150) / 3 = 150
            assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("150"));
        }

        @Test
        @DisplayName("indicator AND 교집합 - RSI AND 볼린저밴드 동시 포함 (trade4 only)")
        void indicator_AND_교집합() {
            // trade4만 RSI와 볼린저밴드 모두 포함 → 1건
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    List.of("RSI", "볼린저밴드"), null, null, null, null, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(1);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(1);  // winCount
            assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("150")); // avgPnl
        }

        @Test
        @DisplayName("timeframe 필터 - 1H 포함 트레이드 (1,3)")
        void timeframe_1H_필터() {
            // trade1(1H), trade3(1H,4H) → 2건, 모두 WIN
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    null, List.of("1H"), null, null, null, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(2);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(2);  // winCount
        }

        @Test
        @DisplayName("positionSide LONG 필터 - 롱 트레이드 (1,3,4)")
        void positionSide_LONG_필터() {
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    null, null, null, null, PositionSide.LONG, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(3);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(3);  // winCount
            assertThat(((Number) row[2]).intValue()).isEqualTo(0);  // lossCount
        }

        @Test
        @DisplayName("positionSide SHORT 필터 - 숏 트레이드 (trade2 only)")
        void positionSide_SHORT_필터() {
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    null, null, null, null, PositionSide.SHORT, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(1);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(0);  // winCount
            assertThat(((Number) row[2]).intValue()).isEqualTo(1);  // lossCount
            assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("-50")); // avgPnl
        }

        @Test
        @DisplayName("marketCondition UPTREND 필터 - 상승장 트레이드 (1,3)")
        void marketCondition_UPTREND_필터() {
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    null, null, null, null, null, MarketCondition.UPTREND);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(2);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(2);  // winCount
        }

        @Test
        @DisplayName("tradingStyle SCALPING 필터 - 1일 미만 (1,3)")
        void tradingStyle_SCALPING_필터() {
            // trade1(2h), trade3(30min) → 2건
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    null, null, null, "SCALPING", null, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(2);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(2);  // winCount
            // avgPnl = (200 + 100) / 2 = 150
            assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("150"));
        }

        @Test
        @DisplayName("tradingStyle SWING 필터 - 1일 이상 (2,4)")
        void tradingStyle_SWING_필터() {
            // trade2(3days), trade4(5days) → 2건, 승1 패1
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    null, null, null, "SWING", null, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(2);  // totalTrades
            assertThat(((Number) row[1]).intValue()).isEqualTo(1);  // winCount (trade4)
            assertThat(((Number) row[2]).intValue()).isEqualTo(1);  // lossCount (trade2)
            // avgPnl = (-50 + 150) / 2 = 50
            assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("50"));
        }

        @Test
        @DisplayName("복합 필터 - RSI + SCALPING (1,3)")
        void 복합필터_RSI_AND_SCALPING() {
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    List.of("RSI"), null, null, "SCALPING", null, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            // trade1(RSI, 2h), trade3(RSI, 30min) → 2건, 모두 WIN
            assertThat(((Number) row[0]).intValue()).isEqualTo(2);
            assertThat(((Number) row[1]).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("복합 필터 - 1H + LONG (1,3)")
        void 복합필터_1H_AND_LONG() {
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    null, List.of("1H"), null, null, PositionSide.LONG, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(2);
            assertThat(((Number) row[1]).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("매칭 결과 없음 - totalTrades 0 반환")
        void 매칭없음_0반환() {
            // 존재하지 않는 indicator
            JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                    List.of("존재하지않는지표"), null, null, null, null, null);

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), filter);

            assertThat(((Number) row[0]).intValue()).isEqualTo(0);  // totalTrades
            assertThat(row[3]).isNull();  // avgPnl (데이터 없으면 null)
            assertThat(row[4]).isNull();  // avgRoi
        }

        @Test
        @DisplayName("다른 사용자의 데이터는 통계에 포함되지 않음")
        void 다른사용자_데이터_제외() {
            User otherUser = em.persist(User.builder()
                    .email("other2@test.com").username("otherUser2").build());
            Position pos = em.persist(Position.builder()
                    .user(otherUser).symbol("BTCUSDT").side(PositionSide.LONG)
                    .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).currentSize(BigDecimal.ZERO)
                    .leverage(10).realizedPnl(new BigDecimal("9999"))
                    .entryTime(now.minusHours(1)).exitTime(now)
                    .status(PositionStatus.CLOSED).build());
            em.persist(TradingJournal.builder().user(otherUser).position(pos).build());
            em.flush();
            em.clear();

            Object[] row = tradingJournalRepository.getJournalStats(user.getId(), emptyFilter());

            // otherUser의 +9999 pnl이 포함되면 avgPnl이 달라짐
            assertThat(((Number) row[0]).intValue()).isEqualTo(4);
            assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("100")); // avgPnl
        }

        private JournalStatsFilterRequest emptyFilter() {
            return new JournalStatsFilterRequest(null, null, null, null, null, null);
        }
    }
}
