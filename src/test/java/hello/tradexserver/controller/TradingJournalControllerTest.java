package hello.tradexserver.controller;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import hello.tradexserver.repository.UserRepository;
import hello.tradexserver.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 매매일지 통계 API 통합 테스트
 *
 * 주의: @Transactional 테스트에서 저장한 데이터는 MockMvc 요청의 새 트랜잭션에서
 * 보이지 않을 수 있습니다. 실제 DB와 함께 실행하려면 @Disabled 제거 후
 * 테스트 클래스에서 @Transactional을 제거하고 @AfterEach에서 수동 정리가 필요합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Disabled
class TradingJournalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private TradingJournalRepository tradingJournalRepository;

    private User testUser;
    private final LocalDateTime now = LocalDateTime.now();

    /**
     * 테스트 데이터:
     * - trade1: RSI+MACD | 1H | 지지저항 | LONG | +200 | UPTREND | SCALPING(2h)
     * - trade2: 볼린저밴드 | 4H | 추세선 | SHORT | -50 | DOWNTREND | SWING(3days)
     * - trade3: RSI | 1H+4H | 지지저항 | LONG | +100 | UPTREND | SCALPING(30min)
     * - trade4: RSI+볼린저밴드 | 1D | 지지저항+추세선 | LONG | +150 | SIDEWAYS | SWING(5days)
     */
    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("journal-ctrl-test@test.com")
                .username("journalCtrlTestUser")
                .build());

        setSecurityContext(testUser);

        // trade1
        Position p1 = positionRepository.save(Position.builder()
                .user(testUser).symbol("BTCUSDT").side(PositionSide.LONG)
                .marketCondition(MarketCondition.UPTREND)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).leverage(10)
                .realizedPnl(new BigDecimal("200"))
                .entryTime(now.minusHours(2)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        tradingJournalRepository.save(TradingJournal.builder().user(testUser).position(p1)
                .indicators(new ArrayList<>(List.of("RSI", "MACD")))
                .timeframes(new ArrayList<>(List.of("1H")))
                .technicalAnalyses(new ArrayList<>(List.of("지지저항")))
                .build());

        // trade2
        Position p2 = positionRepository.save(Position.builder()
                .user(testUser).symbol("BTCUSDT").side(PositionSide.SHORT)
                .marketCondition(MarketCondition.DOWNTREND)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).leverage(10)
                .realizedPnl(new BigDecimal("-50"))
                .entryTime(now.minusDays(3)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        tradingJournalRepository.save(TradingJournal.builder().user(testUser).position(p2)
                .indicators(new ArrayList<>(List.of("볼린저밴드")))
                .timeframes(new ArrayList<>(List.of("4H")))
                .technicalAnalyses(new ArrayList<>(List.of("추세선")))
                .build());

        // trade3
        Position p3 = positionRepository.save(Position.builder()
                .user(testUser).symbol("ETHUSDT").side(PositionSide.LONG)
                .marketCondition(MarketCondition.UPTREND)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).leverage(10)
                .realizedPnl(new BigDecimal("100"))
                .entryTime(now.minusMinutes(30)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        tradingJournalRepository.save(TradingJournal.builder().user(testUser).position(p3)
                .indicators(new ArrayList<>(List.of("RSI")))
                .timeframes(new ArrayList<>(List.of("1H", "4H")))
                .technicalAnalyses(new ArrayList<>(List.of("지지저항")))
                .build());

        // trade4
        Position p4 = positionRepository.save(Position.builder()
                .user(testUser).symbol("ETHUSDT").side(PositionSide.LONG)
                .marketCondition(MarketCondition.SIDEWAYS)
                .avgEntryPrice(new BigDecimal("40000")).closedSize(new BigDecimal("0.1")).leverage(10)
                .realizedPnl(new BigDecimal("150"))
                .entryTime(now.minusDays(5)).exitTime(now)
                .status(PositionStatus.CLOSED).build());
        tradingJournalRepository.save(TradingJournal.builder().user(testUser).position(p4)
                .indicators(new ArrayList<>(List.of("RSI", "볼린저밴드")))
                .timeframes(new ArrayList<>(List.of("1D")))
                .technicalAnalyses(new ArrayList<>(List.of("지지저항", "추세선")))
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /api/journals/stats/options ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/journals/stats/options - 고유값 목록 반환")
    void getStatsOptions_성공() throws Exception {
        mockMvc.perform(get("/api/journals/stats/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.indicators").isArray())
                .andExpect(jsonPath("$.data.timeframes").isArray())
                .andExpect(jsonPath("$.data.technicalAnalyses").isArray())
                // MACD, RSI, 볼린저밴드 (3개)
                .andExpect(jsonPath("$.data.indicators.length()").value(3))
                // 1D, 1H, 4H (3개)
                .andExpect(jsonPath("$.data.timeframes.length()").value(3))
                // 지지저항, 추세선 (2개)
                .andExpect(jsonPath("$.data.technicalAnalyses.length()").value(2));
    }

    // ─── GET /api/journals/stats ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/journals/stats - 필터 없음: 전체 통계 (4건)")
    void getStats_필터없음() throws Exception {
        // trade1(+200) + trade2(-50) + trade3(+100) + trade4(+150) = 4건, 승3 패1
        mockMvc.perform(get("/api/journals/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTrades").value(4))
                .andExpect(jsonPath("$.data.winCount").value(3))
                .andExpect(jsonPath("$.data.lossCount").value(1))
                .andExpect(jsonPath("$.data.winRate").value(75.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats?indicators=RSI - RSI 포함 3건")
    void getStats_indicator_RSI_필터() throws Exception {
        // trade1, trade3, trade4 → 3건, 모두 WIN
        mockMvc.perform(get("/api/journals/stats")
                        .param("indicators", "RSI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(3))
                .andExpect(jsonPath("$.data.winCount").value(3))
                .andExpect(jsonPath("$.data.winRate").value(100.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats?indicators=RSI&indicators=볼린저밴드 - AND 교집합: trade4 only")
    void getStats_indicator_AND_교집합() throws Exception {
        // RSI AND 볼린저밴드 동시 포함 → trade4만 해당
        mockMvc.perform(get("/api/journals/stats")
                        .param("indicators", "RSI")
                        .param("indicators", "볼린저밴드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(1))
                .andExpect(jsonPath("$.data.winCount").value(1))
                .andExpect(jsonPath("$.data.winRate").value(100.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats?tradingStyle=SCALPING - 1일 미만 보유 (1,3)")
    void getStats_tradingStyle_SCALPING() throws Exception {
        // trade1(2h), trade3(30min) → 2건, 모두 WIN
        mockMvc.perform(get("/api/journals/stats")
                        .param("tradingStyle", "SCALPING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(2))
                .andExpect(jsonPath("$.data.winCount").value(2))
                .andExpect(jsonPath("$.data.winRate").value(100.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats?tradingStyle=SWING - 1일 이상 보유 (2,4)")
    void getStats_tradingStyle_SWING() throws Exception {
        // trade2(3days, LOSS), trade4(5days, WIN) → 2건, 승1 패1
        mockMvc.perform(get("/api/journals/stats")
                        .param("tradingStyle", "SWING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(2))
                .andExpect(jsonPath("$.data.winCount").value(1))
                .andExpect(jsonPath("$.data.lossCount").value(1))
                .andExpect(jsonPath("$.data.winRate").value(50.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats?positionSide=LONG - 롱 포지션 (1,3,4)")
    void getStats_positionSide_LONG() throws Exception {
        mockMvc.perform(get("/api/journals/stats")
                        .param("positionSide", "LONG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(3))
                .andExpect(jsonPath("$.data.winCount").value(3))
                .andExpect(jsonPath("$.data.winRate").value(100.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats?positionSide=SHORT - 숏 포지션 (trade2 only)")
    void getStats_positionSide_SHORT() throws Exception {
        mockMvc.perform(get("/api/journals/stats")
                        .param("positionSide", "SHORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(1))
                .andExpect(jsonPath("$.data.winCount").value(0))
                .andExpect(jsonPath("$.data.lossCount").value(1))
                .andExpect(jsonPath("$.data.winRate").value(0.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats?marketCondition=UPTREND - 상승장 (1,3)")
    void getStats_marketCondition_UPTREND() throws Exception {
        mockMvc.perform(get("/api/journals/stats")
                        .param("marketCondition", "UPTREND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(2))
                .andExpect(jsonPath("$.data.winCount").value(2))
                .andExpect(jsonPath("$.data.winRate").value(100.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats - 복합 필터: 1H + LONG (1,3)")
    void getStats_복합필터_1H_AND_LONG() throws Exception {
        mockMvc.perform(get("/api/journals/stats")
                        .param("timeframes", "1H")
                        .param("positionSide", "LONG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(2))
                .andExpect(jsonPath("$.data.winRate").value(100.00));
    }

    @Test
    @DisplayName("GET /api/journals/stats - 매칭 없는 필터: totalTrades=0")
    void getStats_매칭없음() throws Exception {
        mockMvc.perform(get("/api/journals/stats")
                        .param("indicators", "존재하지않는지표"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(0))
                .andExpect(jsonPath("$.data.winRate").value(0.00));
    }

    private void setSecurityContext(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(
                user.getId(), user.getEmail(), null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }
}
