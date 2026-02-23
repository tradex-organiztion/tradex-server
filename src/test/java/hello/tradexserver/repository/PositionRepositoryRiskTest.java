package hello.tradexserver.repository;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
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

/**
 * PositionRepository.findClosedWithJournalForRiskAnalysis 전용 테스트
 *
 * 테스트 데이터:
 *   P1 BYBIT  BTCUSDT  CLOSED  entry=T-5d exit=T-4d  journal(scenario="계획")
 *   P2 BYBIT  ETHUSDT  CLOSED  entry=T-3d exit=T-2d  journal(scenario=null)
 *   P3 BINANCE SOLUSDT CLOSED  entry=T-1d exit=T-12h journal(scenario="계획")
 *   P4 BYBIT  XRPUSDT OPEN    entry=T-1h             (집계 제외 대상)
 *   P5 BYBIT  DOTUSDT CLOSED  entry=T-20d exit=T-19d journal (날짜 범위 테스트용)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PositionRepositoryRiskTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PositionRepository positionRepository;

    private User user;
    private final LocalDateTime NOW = LocalDateTime.now();

    private Position p1, p2, p3, p4, p5;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder()
                .email("risk-repo-test@test.com")
                .username("riskRepoTestUser")
                .build());

        // P1: BYBIT, CLOSED, 5일 전 진입 / 4일 전 종료, 시나리오 있음
        p1 = em.persist(Position.builder()
                .user(user).symbol("BTCUSDT").side(PositionSide.LONG)
                .exchangeName(ExchangeName.BYBIT)
                .avgEntryPrice(new BigDecimal("40000"))
                .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("0.1")).leverage(10)
                .realizedPnl(new BigDecimal("200"))
                .entryTime(NOW.minusDays(5)).exitTime(NOW.minusDays(4))
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder()
                .user(user).position(p1)
                .entryScenario("계획된 매매 시나리오")
                .plannedStopLoss(new BigDecimal("39000"))
                .plannedTargetPrice(new BigDecimal("42000"))
                .indicators(new ArrayList<>(List.of("RSI")))
                .build());

        // P2: BYBIT, CLOSED, 3일 전 진입 / 2일 전 종료, 시나리오 없음
        p2 = em.persist(Position.builder()
                .user(user).symbol("ETHUSDT").side(PositionSide.SHORT)
                .exchangeName(ExchangeName.BYBIT)
                .avgEntryPrice(new BigDecimal("2500"))
                .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("1")).leverage(5)
                .realizedPnl(new BigDecimal("-50"))
                .entryTime(NOW.minusDays(3)).exitTime(NOW.minusDays(2))
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder()
                .user(user).position(p2)
                // entryScenario = null (계획 외 진입)
                .indicators(new ArrayList<>())
                .build());

        // P3: BINANCE, CLOSED, 1일 전 진입 / 12시간 전 종료, 시나리오 있음
        p3 = em.persist(Position.builder()
                .user(user).symbol("SOLUSDT").side(PositionSide.LONG)
                .exchangeName(ExchangeName.BINANCE)
                .avgEntryPrice(new BigDecimal("100"))
                .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("10")).leverage(3)
                .realizedPnl(new BigDecimal("30"))
                .entryTime(NOW.minusDays(1)).exitTime(NOW.minusHours(12))
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder()
                .user(user).position(p3)
                .entryScenario("BINANCE 매매 계획")
                .indicators(new ArrayList<>(List.of("MACD")))
                .build());

        // P4: BYBIT, OPEN — 집계 제외 대상
        p4 = em.persist(Position.builder()
                .user(user).symbol("XRPUSDT").side(PositionSide.LONG)
                .exchangeName(ExchangeName.BYBIT)
                .avgEntryPrice(new BigDecimal("0.5"))
                .currentSize(new BigDecimal("1000")).leverage(10)
                .entryTime(NOW.minusHours(1))
                .status(PositionStatus.OPEN).build());

        // P5: BYBIT, CLOSED, 20일 전 진입 / 19일 전 종료 (날짜 범위 테스트용)
        p5 = em.persist(Position.builder()
                .user(user).symbol("DOTUSDT").side(PositionSide.LONG)
                .exchangeName(ExchangeName.BYBIT)
                .avgEntryPrice(new BigDecimal("7"))
                .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("100")).leverage(5)
                .realizedPnl(new BigDecimal("100"))
                .entryTime(NOW.minusDays(20)).exitTime(NOW.minusDays(19))
                .status(PositionStatus.CLOSED).build());
        em.persist(TradingJournal.builder()
                .user(user).position(p5)
                .entryScenario("오래된 포지션")
                .indicators(new ArrayList<>())
                .build());

        em.flush();
        em.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 거래소 필터
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("거래소 필터")
    class ExchangeFilter {

        @Test
        @DisplayName("exchangeName=null이면 전체 거래소 CLOSED 포지션 반환")
        void exchangeName_null_전체반환() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, null, null);

            // P1, P2, P3, P5 (CLOSED 4건, OPEN P4 제외)
            assertThat(result).hasSize(4);
            assertThat(result).allMatch(p -> p.getStatus() == PositionStatus.CLOSED);
        }

        @Test
        @DisplayName("BYBIT만 필터링하면 BYBIT CLOSED 포지션 반환")
        void bybit_filter_3건반환() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), ExchangeName.BYBIT, null, null);

            assertThat(result).hasSize(3); // P1, P2, P5
            assertThat(result).allMatch(p -> p.getExchangeName() == ExchangeName.BYBIT);
        }

        @Test
        @DisplayName("BINANCE만 필터링하면 BINANCE CLOSED 포지션만 반환")
        void binance_filter_1건반환() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), ExchangeName.BINANCE, null, null);

            assertThat(result).hasSize(1); // P3
            assertThat(result.get(0).getExchangeName()).isEqualTo(ExchangeName.BINANCE);
            assertThat(result.get(0).getSymbol()).isEqualTo("SOLUSDT");
        }

        @Test
        @DisplayName("존재하지 않는 거래소는 빈 결과 반환")
        void 없는거래소_빈결과() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), ExchangeName.BITGET, null, null);

            assertThat(result).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 날짜 범위 필터
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("날짜 범위 필터 (exitTime 기준)")
    class DateRangeFilter {

        @Test
        @DisplayName("startDate=null이면 전체 기간 조회 (all 기간 지원)")
        void startDate_null_전체기간() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, null, null);

            assertThat(result).hasSize(4); // P1, P2, P3, P5 모두
        }

        @Test
        @DisplayName("startDate 설정 시 exitTime >= startDate 포지션만 반환")
        void startDate_3일전_2건반환() {
            // startDate = NOW-3d → P2(exit NOW-2d), P3(exit NOW-12h) 포함
            // P1(exit NOW-4d), P5(exit NOW-19d)는 제외
            LocalDateTime startDate = NOW.minusDays(3);

            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, startDate, null);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> !p.getExitTime().isBefore(startDate));
        }

        @Test
        @DisplayName("endDate 설정 시 exitTime <= endDate 포지션만 반환")
        void endDate_4일전_P1_P5반환() {
            // endDate = NOW-4d → P1(exit NOW-4d), P5(exit NOW-19d) 포함
            // P2(exit NOW-2d), P3(exit NOW-12h)는 제외
            LocalDateTime endDate = NOW.minusDays(4);

            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, null, endDate);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> !p.getExitTime().isAfter(endDate));
        }

        @Test
        @DisplayName("startDate + endDate 조합으로 구간 내 포지션만 반환")
        void startDate_endDate_구간조회() {
            // startDate=NOW-6d, endDate=NOW-3d → P1(exit NOW-4d) 포함, P5(exit NOW-19d) 제외
            LocalDateTime startDate = NOW.minusDays(6);
            LocalDateTime endDate = NOW.minusDays(3);

            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, startDate, endDate);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSymbol()).isEqualTo("BTCUSDT"); // P1
        }

        @Test
        @DisplayName("거래소 + 날짜 복합 필터")
        void 거래소_날짜_복합필터() {
            // BYBIT + startDate=NOW-6d → P1(exit NOW-4d), P2(exit NOW-2d) 포함, P5(exit NOW-19d) 제외
            LocalDateTime startDate = NOW.minusDays(6);

            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), ExchangeName.BYBIT, startDate, null);

            assertThat(result).hasSize(2); // P1, P2
            assertThat(result).allMatch(p -> p.getExchangeName() == ExchangeName.BYBIT);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 상태 / 데이터 무결성
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("상태 및 데이터 무결성")
    class DataIntegrity {

        @Test
        @DisplayName("OPEN 포지션은 결과에서 제외")
        void open_포지션_제외() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, null, null);

            assertThat(result)
                    .noneMatch(p -> p.getStatus() == PositionStatus.OPEN);
            assertThat(result)
                    .noneMatch(p -> "XRPUSDT".equals(p.getSymbol())); // P4 제외 확인
        }

        @Test
        @DisplayName("JOIN FETCH로 TradingJournal 즉시 접근 가능 (N+1 없음)")
        void tradingJournal_fetch_join_접근가능() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), ExchangeName.BYBIT, null, null);

            // em.clear() 후에도 TradingJournal 접근 가능 = fetch join 정상 동작
            assertThat(result).hasSize(3);
            result.forEach(p -> {
                TradingJournal journal = p.getTradingJournal();
                assertThat(journal).isNotNull(); // 모든 포지션에 journal 존재
                // entryScenario 접근 시 추가 쿼리 없이 바로 조회
                journal.getEntryScenario(); // LazyInitializationException 발생하지 않아야 함
            });
        }

        @Test
        @DisplayName("TradingJournal의 필드 값이 정확하게 로딩됨")
        void tradingJournal_필드값_정확성() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), ExchangeName.BYBIT, null, null);

            // P1: scenario="계획된 매매 시나리오", plannedStopLoss=39000, plannedTargetPrice=42000
            Position btcPosition = result.stream()
                    .filter(p -> "BTCUSDT".equals(p.getSymbol()))
                    .findFirst().orElseThrow();

            TradingJournal journal = btcPosition.getTradingJournal();
            assertThat(journal.getEntryScenario()).isEqualTo("계획된 매매 시나리오");
            assertThat(journal.getPlannedStopLoss()).isEqualByComparingTo(new BigDecimal("39000"));
            assertThat(journal.getPlannedTargetPrice()).isEqualByComparingTo(new BigDecimal("42000"));
        }

        @Test
        @DisplayName("TradingJournal이 없는 포지션은 null로 반환 (LEFT JOIN FETCH)")
        void tradingJournal_없는경우_null() {
            // journal 없는 포지션 추가
            Position noJournalPos = em.persist(Position.builder()
                    .user(user).symbol("BNBUSDT").side(PositionSide.LONG)
                    .exchangeName(ExchangeName.BYBIT)
                    .avgEntryPrice(new BigDecimal("300"))
                    .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("1")).leverage(5)
                    .realizedPnl(new BigDecimal("50"))
                    .entryTime(NOW.minusDays(2)).exitTime(NOW.minusDays(1))
                    .status(PositionStatus.CLOSED).build());
            // TradingJournal 미생성
            em.flush();
            em.clear();

            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), ExchangeName.BYBIT, null, null);

            Position noJournal = result.stream()
                    .filter(p -> "BNBUSDT".equals(p.getSymbol()))
                    .findFirst().orElseThrow();

            assertThat(noJournal.getTradingJournal()).isNull();
        }

        @Test
        @DisplayName("entryTime ASC 정렬 확인")
        void entryTime_asc_정렬() {
            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, null, null);

            // P5(T-20d) < P1(T-5d) < P2(T-3d) < P3(T-1d) 순서
            for (int i = 0; i < result.size() - 1; i++) {
                assertThat(result.get(i).getEntryTime())
                        .isBeforeOrEqualTo(result.get(i + 1).getEntryTime());
            }
        }

        @Test
        @DisplayName("다른 유저의 포지션은 조회되지 않음")
        void 다른유저_포지션_제외() {
            User otherUser = em.persist(User.builder()
                    .email("other-risk@test.com").username("otherRiskUser").build());
            em.persist(Position.builder()
                    .user(otherUser).symbol("BTCUSDT").side(PositionSide.LONG)
                    .exchangeName(ExchangeName.BYBIT)
                    .avgEntryPrice(new BigDecimal("40000"))
                    .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("1")).leverage(10)
                    .realizedPnl(new BigDecimal("9999"))
                    .entryTime(NOW.minusDays(1)).exitTime(NOW)
                    .status(PositionStatus.CLOSED).build());
            em.flush();
            em.clear();

            List<Position> result = positionRepository
                    .findClosedWithJournalForRiskAnalysis(user.getId(), null, null, null);

            assertThat(result)
                    .noneMatch(p -> p.getRealizedPnl() != null
                            && p.getRealizedPnl().compareTo(new BigDecimal("9999")) == 0);
            assertThat(result).hasSize(4); // 기존 4건 유지
        }
    }
}
