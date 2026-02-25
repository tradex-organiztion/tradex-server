package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.dto.response.risk.RiskAnalysisResponse;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskAnalysisServiceTest {

    @InjectMocks
    private RiskAnalysisService service;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private OrderRepository orderRepository;

    private static final Long USER_ID = 1L;
    private static final LocalDateTime T = LocalDateTime.of(2025, 1, 15, 10, 0, 0);

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼 — Position / TradingJournal / Order Mock 생성
    // ──────────────────────────────────────────────────────────────────────────

    private Position pos(long id, String symbol, PositionSide side, BigDecimal pnl,
                         LocalDateTime entry, LocalDateTime exit,
                         TradingJournal journal, BigDecimal avgExitPrice,
                         BigDecimal stopLoss, BigDecimal targetPrice) {
        Position p = mock(Position.class);
        when(p.getId()).thenReturn(id);
        when(p.getExchangeName()).thenReturn(ExchangeName.BYBIT);
        when(p.getSymbol()).thenReturn(symbol);
        when(p.getSide()).thenReturn(side);
        when(p.getRealizedPnl()).thenReturn(pnl);
        when(p.getEntryTime()).thenReturn(entry);
        when(p.getExitTime()).thenReturn(exit);
        when(p.getTradingJournal()).thenReturn(journal);
        when(p.getAvgExitPrice()).thenReturn(avgExitPrice);
        when(p.getStopLossPrice()).thenReturn(stopLoss);
        when(p.getTargetPrice()).thenReturn(targetPrice);
        when(p.getMarketCondition()).thenReturn(null);
        return p;
    }

    /** 진입/재진입 시나리오 테스트용 (exit risk 미사용) */
    private Position pos(long id, String symbol, PositionSide side, BigDecimal pnl,
                         LocalDateTime entry, LocalDateTime exit, TradingJournal journal) {
        return pos(id, symbol, side, pnl, entry, exit, journal, null, null, null);
    }

    private TradingJournal journal(String scenario, BigDecimal sl, BigDecimal tp) {
        TradingJournal j = mock(TradingJournal.class);
        when(j.getEntryReason()).thenReturn(scenario);
        when(j.getStopLoss()).thenReturn(sl);
        when(j.getTargetPrice()).thenReturn(tp);
        return j;
    }

    private TradingJournal journalWithScenario(String scenario) {
        return journal(scenario, null, null);
    }

    private Order mockOrder(Position position, BigDecimal price, BigDecimal qty) {
        Order o = mock(Order.class);
        when(o.getPosition()).thenReturn(position);
        when(o.getFilledPrice()).thenReturn(price);
        when(o.getFilledQuantity()).thenReturn(qty);
        return o;
    }

    private void givenPositions(List<Position> positions) {
        given(positionRepository.findClosedWithJournalForRiskAnalysis(any(), any(), any(), any()))
                .willReturn(positions);
        given(orderRepository.findOpenOrdersByPositionIds(any()))
                .willReturn(Collections.emptyList());
    }

    private RiskAnalysisResponse analyze(List<Position> positions) {
        givenPositions(positions);
        return service.analyze(USER_ID, null, "30d", null, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 빈 포지션
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("포지션이 없으면 empty 응답 반환")
    void emptyPositions_returnsEmptyResponse() {
        given(positionRepository.findClosedWithJournalForRiskAnalysis(any(), any(), any(), any()))
                .willReturn(Collections.emptyList());

        RiskAnalysisResponse result = service.analyze(USER_ID, null, "30d", null, null);

        assertThat(result.getTotalTrades()).isZero();
        assertThat(result.getEntryRisk().getUnplannedEntryCount()).isZero();
        assertThat(result.getExitRisk().getSlViolationCount()).isZero();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1️⃣ 진입 리스크: 계획 외 진입
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("계획 외 진입")
    class UnplannedEntry {

        @Test
        @DisplayName("entryScenario 없는 포지션은 계획 외 진입으로 카운트")
        void noScenario_countedAsUnplanned() {
            // 계획 진입 1, 계획 외 진입 2 (null journal / 빈 시나리오)
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(100), T, T.plusHours(1), journalWithScenario("진입 계획")),
                    pos(2L, "ETHUSDT", PositionSide.LONG, bd(-50), T.plusHours(2), T.plusHours(3), null),
                    pos(3L, "SOLUSDT", PositionSide.SHORT, bd(30), T.plusHours(4), T.plusHours(5), journalWithScenario(""))
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getUnplannedEntryCount()).isEqualTo(2);
            assertThat(result.getEntryRisk().getUnplannedEntryRate()).isEqualByComparingTo("66.67");
        }

        @Test
        @DisplayName("계획 진입 vs 계획 외 진입 승률 비교")
        void plannedVsUnplanned_winRateDiffers() {
            // 계획 진입: 승 2건, 패 0건 → 승률 100%
            // 계획 외 진입: 승 0건, 패 2건 → 승률 0%
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(100), T, T.plusHours(1), journalWithScenario("계획")),
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(200), T.plusHours(2), T.plusHours(3), journalWithScenario("계획")),
                    pos(3L, "ETHUSDT", PositionSide.LONG, bd(-50), T.plusHours(4), T.plusHours(5), null),
                    pos(4L, "ETHUSDT", PositionSide.LONG, bd(-30), T.plusHours(6), T.plusHours(7), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getPlannedEntryWinRate()).isEqualByComparingTo("100.00");
            assertThat(result.getEntryRisk().getUnplannedEntryWinRate()).isEqualByComparingTo("0.00");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1️⃣ 진입 리스크: 감정적 재진입 (손절 후 즉시 재진입)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("감정적 재진입")
    class EmotionalReEntry {

        @Test
        @DisplayName("손절 후 15분 이내 동일 종목 재진입 + 시나리오 없음 → 감정 매매 카운트")
        void lossAndReentryWithin15min_counted() {
            // A 종료(손실, 10:00) → B 진입(시나리오 없음, 10:08) = 8분 이내
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100), T, T, null),            // A
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(-20), T.plusMinutes(8), T.plusHours(1), null)  // B
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getEmotionalReEntryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("재진입 포지션에 시나리오가 있으면 감정 매매 제외")
        void hasScenarioOnReentry_notCounted() {
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100), T, T, null),
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(50), T.plusMinutes(8), T.plusHours(1), journalWithScenario("사전 계획 있음"))
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getEmotionalReEntryCount()).isZero();
        }

        @Test
        @DisplayName("손절 후 15분 초과 재진입은 감정 매매 제외")
        void reentryAfter15min_notCounted() {
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100), T, T, null),
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(50), T.plusMinutes(20), T.plusHours(1), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getEmotionalReEntryCount()).isZero();
        }

        @Test
        @DisplayName("익절 후 재진입은 감정 매매 아님 (이전 PnL > 0)")
        void profitThenReentry_notEmotional() {
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(100), T, T, null),   // 익절
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(-30), T.plusMinutes(5), T.plusHours(1), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getEmotionalReEntryCount()).isZero();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1️⃣ 진입 리스크: 뇌동매매 (연속 진입)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("뇌동매매 (연속 진입)")
    class ImpulsiveTrade {

        @Test
        @DisplayName("동일 종목 15분 이내 재진입이 3회 연속 이상이면 뇌동매매 카운트")
        void consecutiveReentry3Times_counted() {
            // A→B (5분) → B→C (2분) → C→D (3분): 체인 길이 4 → 모두 뇌동매매
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-10), T,                   T.plusMinutes(5),  null),   // A
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(-10), T.plusMinutes(5),    T.plusMinutes(7),  null),   // B (A.exit→B.entry: 0분)
                    pos(3L, "BTCUSDT", PositionSide.LONG, bd(-10), T.plusMinutes(7),    T.plusMinutes(10), null),   // C
                    pos(4L, "BTCUSDT", PositionSide.LONG, bd(-10), T.plusMinutes(10),   T.plusHours(2),    null)    // D
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getImpulsiveTradeCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("연속 진입 체인 길이가 2면 뇌동매매 미해당")
        void consecutiveReentry2Times_notCounted() {
            // A→B (5분): 체인 길이 2 → 미해당
            // C는 B 종료 50분 후 → 체인 끊김
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-10), T,              T.plusMinutes(5),  null),  // A
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(-10), T.plusMinutes(5), T.plusMinutes(7), null),  // B
                    pos(3L, "BTCUSDT", PositionSide.LONG, bd(50),  T.plusHours(1), T.plusHours(2),    null)   // C (체인 끊김)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEntryRisk().getImpulsiveTradeCount()).isZero();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2️⃣ 청산 리스크: 손절가 미준수
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("손절가 미준수")
    class SlViolation {

        @Test
        @DisplayName("LONG: 실제 청산가가 SL 대비 0.3% 초과하여 불리하면 위반")
        void long_exitBelowSlThreshold_violated() {
            // SL=50000, threshold=49850 (50000*0.997), exit=49800 → 위반
            TradingJournal j = journal("계획", bd(50000), null);
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(-200),
                    T, T.plusHours(1), j, bd(49800), null, null);

            RiskAnalysisResponse result = analyze(List.of(p));

            assertThat(result.getExitRisk().getSlViolationCount()).isEqualTo(1);
            assertThat(result.getExitRisk().getSlViolationRate()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("LONG: 실제 청산가가 SL 오차 범위 이내면 위반 아님")
        void long_exitWithinTolerance_notViolated() {
            // SL=50000, threshold=49850, exit=49900 → 준수
            TradingJournal j = journal("계획", bd(50000), null);
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100),
                    T, T.plusHours(1), j, bd(49900), null, null);

            RiskAnalysisResponse result = analyze(List.of(p));

            assertThat(result.getExitRisk().getSlViolationCount()).isZero();
        }

        @Test
        @DisplayName("SHORT: 실제 청산가가 SL 대비 0.3% 초과하여 불리하면 위반")
        void short_exitAboveSlThreshold_violated() {
            // SL=50000, threshold=50150 (50000*1.003), exit=50200 → 위반
            TradingJournal j = journal("계획", bd(50000), null);
            Position p = pos(1L, "BTCUSDT", PositionSide.SHORT, bd(-200),
                    T, T.plusHours(1), j, bd(50200), null, null);

            RiskAnalysisResponse result = analyze(List.of(p));

            assertThat(result.getExitRisk().getSlViolationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("SL 미설정 포지션은 집계 대상 제외")
        void noSl_excluded() {
            // journal은 있지만 SL 미설정, Position에도 stopLossPrice 없음
            TradingJournal j = journal("계획", null, null);
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(-200),
                    T, T.plusHours(1), j, bd(48000), null, null);

            RiskAnalysisResponse result = analyze(List.of(p));

            assertThat(result.getExitRisk().getSlViolationCount()).isZero();
            assertThat(result.getExitRisk().getSlViolationRate()).isNull(); // 분모 0
        }

        @Test
        @DisplayName("평균 손절 지연: SL 위반 케이스들의 오차율 평균")
        void avgSlDelay_calculatedCorrectly() {
            // A: SL=50000, exit=49000 → delay=(49000-50000)/50000*100 = -2.0%
            // B: SL=50000, exit=49500 → delay=(49500-50000)/50000*100 = -1.0%
            // avgSlDelay = -1.5%
            TradingJournal jA = journal("계획", bd(50000), null);
            TradingJournal jB = journal("계획", bd(50000), null);
            Position pA = pos(1L, "BTCUSDT", PositionSide.LONG, bd(-1000), T, T.plusHours(1), jA, bd(49000), null, null);
            Position pB = pos(2L, "ETHUSDT", PositionSide.LONG, bd(-500), T.plusHours(2), T.plusHours(3), jB, bd(49500), null, null);

            RiskAnalysisResponse result = analyze(List.of(pA, pB));

            assertThat(result.getExitRisk().getSlViolationCount()).isEqualTo(2);
            assertThat(result.getExitRisk().getAvgSlDelay()).isEqualByComparingTo("-1.5000");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2️⃣ 청산 리스크: 조기 익절
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("조기 익절")
    class EarlyTp {

        @Test
        @DisplayName("LONG: 실제 청산가가 TP 미달이면 조기 익절")
        void long_exitBelowTp_earlyTp() {
            // TP=55000, exit=54000 → 조기 익절
            TradingJournal j = journal("계획", null, bd(55000));
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(400),
                    T, T.plusHours(1), j, bd(54000), null, null);

            RiskAnalysisResponse result = analyze(List.of(p));

            assertThat(result.getExitRisk().getEarlyTpCount()).isEqualTo(1);
            assertThat(result.getExitRisk().getEarlyTpRate()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("LONG: 실제 청산가가 TP 이상이면 조기 익절 아님")
        void long_exitAtOrAboveTp_notEarlyTp() {
            // TP=55000, exit=56000 → 정상 익절
            TradingJournal j = journal("계획", null, bd(55000));
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(600),
                    T, T.plusHours(1), j, bd(56000), null, null);

            RiskAnalysisResponse result = analyze(List.of(p));

            assertThat(result.getExitRisk().getEarlyTpCount()).isZero();
        }

        @Test
        @DisplayName("TP 미설정 포지션은 집계 대상 제외")
        void noTp_excluded() {
            TradingJournal j = journal("계획", null, null);
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(300),
                    T, T.plusHours(1), j, bd(54000), null, null);

            RiskAnalysisResponse result = analyze(List.of(p));

            assertThat(result.getExitRisk().getEarlyTpRate()).isNull(); // 분모 0
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3️⃣ 포지션 관리 리스크
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("포지션 관리 리스크")
    class PositionManagement {

        @Test
        @DisplayName("평균 손익비(R/R) = avgWin / avgLoss")
        void rrRatio_calculatedCorrectly() {
            // 승: 100, 200, 300 → avgWin = 200
            // 패: 50, 150 → avgLoss = 100
            // R/R = 200 / 100 = 2.0
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(100), T, T.plusHours(1), null),
                    pos(2L, "ETHUSDT", PositionSide.LONG, bd(200), T.plusHours(1), T.plusHours(2), null),
                    pos(3L, "SOLUSDT", PositionSide.LONG, bd(300), T.plusHours(2), T.plusHours(3), null),
                    pos(4L, "BTCUSDT", PositionSide.SHORT, bd(-50), T.plusHours(3), T.plusHours(4), null),
                    pos(5L, "ETHUSDT", PositionSide.SHORT, bd(-150), T.plusHours(4), T.plusHours(5), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getPositionManagementRisk().getAvgRrRatio()).isEqualByComparingTo("2.0000");
        }

        @Test
        @DisplayName("물타기: LONG 손실 구간에서 추가 진입하면 감지")
        void averagingDown_long_detected() {
            // LONG 포지션
            // 1차 진입: price=40000
            // 2차 진입: price=39000 < 40000 → 손실 구간 추가 진입 = 물타기
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100), T, T.plusHours(1), null);
            Order order1 = mockOrder(p, bd(40000), bd("0.1"));
            Order order2 = mockOrder(p, bd(39000), bd("0.1"));

            given(positionRepository.findClosedWithJournalForRiskAnalysis(any(), any(), any(), any()))
                    .willReturn(List.of(p));
            given(orderRepository.findOpenOrdersByPositionIds(any()))
                    .willReturn(List.of(order1, order2));

            RiskAnalysisResponse result = service.analyze(USER_ID, null, "30d", null, null);

            assertThat(result.getPositionManagementRisk().getAveragingDownCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("물타기 없음: LONG 수익 구간에서 추가 진입하면 미감지")
        void averagingDown_long_notDetectedWhenProfitable() {
            // 1차 진입: price=40000
            // 2차 진입: price=41000 > 40000 → 수익 구간 추가 진입 = 물타기 아님
            Position p = pos(1L, "BTCUSDT", PositionSide.LONG, bd(200), T, T.plusHours(1), null);
            Order order1 = mockOrder(p, bd(40000), bd("0.1"));
            Order order2 = mockOrder(p, bd(41000), bd("0.1"));

            given(positionRepository.findClosedWithJournalForRiskAnalysis(any(), any(), any(), any()))
                    .willReturn(List.of(p));
            given(orderRepository.findOpenOrdersByPositionIds(any()))
                    .willReturn(List.of(order1, order2));

            RiskAnalysisResponse result = service.analyze(USER_ID, null, "30d", null, null);

            assertThat(result.getPositionManagementRisk().getAveragingDownCount()).isZero();
        }

        @Test
        @DisplayName("물타기: SHORT 손실 구간에서 추가 진입하면 감지")
        void averagingDown_short_detected() {
            // SHORT 포지션: 평균 진입가보다 높은 가격에 추가 진입 = 손실 구간
            // 1차: price=40000, 2차: price=41000 > 40000 → SHORT 물타기
            Position p = pos(1L, "BTCUSDT", PositionSide.SHORT, bd(-100), T, T.plusHours(1), null);
            Order order1 = mockOrder(p, bd(40000), bd("0.1"));
            Order order2 = mockOrder(p, bd(41000), bd("0.1"));

            given(positionRepository.findClosedWithJournalForRiskAnalysis(any(), any(), any(), any()))
                    .willReturn(List.of(p));
            given(orderRepository.findOpenOrdersByPositionIds(any()))
                    .willReturn(List.of(order1, order2));

            RiskAnalysisResponse result = service.analyze(USER_ID, null, "30d", null, null);

            assertThat(result.getPositionManagementRisk().getAveragingDownCount()).isEqualTo(1);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5️⃣ 감정 리스크
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("감정 리스크")
    class EmotionalRisk {

        @Test
        @DisplayName("과신 진입: 익절 직후 15분 이내 재진입 후 손실이면 카운트")
        void overconfidentEntry_winThenLossWithin15min_counted() {
            // A 익절(10:00) → B 진입(10:05) 손실 → 과신 진입
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(200), T, T, null),
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(-80), T.plusMinutes(5), T.plusHours(1), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEmotionalRisk().getOverconfidentEntryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("과신 진입: 익절 후 재진입이 수익이면 미해당")
        void overconfidentEntry_winThenWin_notCounted() {
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(200), T, T, null),
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(100), T.plusMinutes(5), T.plusHours(1), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEmotionalRisk().getOverconfidentEntryCount()).isZero();
        }

        @Test
        @DisplayName("손절 후 즉시 역포지션: 손절 후 15분 이내 반대 방향 진입이면 카운트")
        void immediateReverse_afterLoss_counted() {
            // LONG 손절(10:00) → SHORT 진입(10:05)
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100), T, T, null),
                    pos(2L, "BTCUSDT", PositionSide.SHORT, bd(50), T.plusMinutes(5), T.plusHours(1), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEmotionalRisk().getImmediateReverseCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("손절 후 즉시 역포지션: 같은 방향 재진입은 역포지션 아님")
        void immediateReverse_sameSide_notCounted() {
            // LONG 손절 → LONG 재진입 = 역포지션 아님 (감정적 재진입에 해당)
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100), T, T, null),
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(50), T.plusMinutes(5), T.plusHours(1), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEmotionalRisk().getImmediateReverseCount()).isZero();
        }

        @Test
        @DisplayName("감정 매매 카운트는 entryRisk.emotionalReEntryCount와 동일")
        void emotionalTradeCount_sameAsEmotionalReEntry() {
            List<Position> positions = List.of(
                    pos(1L, "BTCUSDT", PositionSide.LONG, bd(-100), T, T, null),
                    pos(2L, "BTCUSDT", PositionSide.LONG, bd(-20), T.plusMinutes(5), T.plusHours(1), null)
            );

            RiskAnalysisResponse result = analyze(positions);

            assertThat(result.getEmotionalRisk().getEmotionalTradeCount())
                    .isEqualTo(result.getEntryRisk().getEmotionalReEntryCount());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 유틸리티
    // ──────────────────────────────────────────────────────────────────────────

    private BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
