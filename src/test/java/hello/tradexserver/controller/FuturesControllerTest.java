package hello.tradexserver.controller;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.repository.PositionRepository;
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
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Disabled
class FuturesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PositionRepository positionRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 테스트 유저 생성
        testUser = userRepository.save(User.builder()
                .email("futures-test@test.com")
                .username("futuresTestUser")
                .build());

        // Security Context 설정
        CustomUserDetails userDetails = new CustomUserDetails(
                testUser.getId(),
                testUser.getEmail(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        // 테스트 데이터: 종료된 포지션들
        LocalDateTime now = LocalDateTime.now();

        // BTCUSDT LONG - 수익
        positionRepository.save(Position.builder()
                .user(testUser)
                .symbol("BTCUSDT")
                .side(PositionSide.LONG)
                .avgEntryPrice(new BigDecimal("40000"))
                .avgExitPrice(new BigDecimal("42000"))
                .closedSize(new BigDecimal("0.1"))
                .leverage(10)
                .realizedPnl(new BigDecimal("200"))
                .entryTime(now.minusDays(2))
                .exitTime(now.minusDays(1))
                .openFee(new BigDecimal("4"))
                .closedFee(new BigDecimal("4.2"))
                .status(PositionStatus.CLOSED)
                .build());

        // BTCUSDT SHORT - 손실
        positionRepository.save(Position.builder()
                .user(testUser)
                .symbol("BTCUSDT")
                .side(PositionSide.SHORT)
                .avgEntryPrice(new BigDecimal("41000"))
                .avgExitPrice(new BigDecimal("42000"))
                .closedSize(new BigDecimal("0.05"))
                .leverage(10)
                .realizedPnl(new BigDecimal("-50"))
                .entryTime(now.minusDays(3))
                .exitTime(now.minusDays(2))
                .openFee(new BigDecimal("2"))
                .closedFee(new BigDecimal("2.1"))
                .status(PositionStatus.CLOSED)
                .build());

        // ETHUSDT LONG - 수익
        positionRepository.save(Position.builder()
                .user(testUser)
                .symbol("ETHUSDT")
                .side(PositionSide.LONG)
                .avgEntryPrice(new BigDecimal("2500"))
                .avgExitPrice(new BigDecimal("2700"))
                .closedSize(new BigDecimal("1"))
                .leverage(5)
                .realizedPnl(new BigDecimal("100"))
                .entryTime(now.minusDays(5))
                .exitTime(now.minusDays(4))
                .openFee(new BigDecimal("1.25"))
                .closedFee(new BigDecimal("1.35"))
                .status(PositionStatus.CLOSED)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/futures/summary - 선물 거래 요약")
    void getFuturesSummary() throws Exception {
        mockMvc.perform(get("/api/futures/summary")
                        .param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPnl").value(250.00))  // 200 - 50 + 100
                .andExpect(jsonPath("$.data.winCount").value(2))
                .andExpect(jsonPath("$.data.lossCount").value(1))
                .andExpect(jsonPath("$.data.totalTradeCount").value(3));
    }

    @Test
    @DisplayName("GET /api/futures/profit-ranking - 페어별 손익 랭킹")
    void getProfitRanking() throws Exception {
        mockMvc.perform(get("/api/futures/profit-ranking")
                        .param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rankings").isArray())
                .andExpect(jsonPath("$.data.rankings[0].symbol").value("BTCUSDT"))  // 150 > 100
                .andExpect(jsonPath("$.data.rankings[0].totalPnl").value(150.00));
    }

    @Test
    @DisplayName("GET /api/futures/closed-positions/summary - 종료 포지션 요약")
    void getClosedPositionsSummary() throws Exception {
        mockMvc.perform(get("/api/futures/closed-positions/summary")
                        .param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalClosedCount").value(3))
                .andExpect(jsonPath("$.data.longPnl").value(300.00))   // 200 + 100
                .andExpect(jsonPath("$.data.longCount").value(2))
                .andExpect(jsonPath("$.data.shortPnl").value(-50.00))
                .andExpect(jsonPath("$.data.shortCount").value(1));
    }

    @Test
    @DisplayName("GET /api/futures/closed-positions - 종료 포지션 목록")
    void getClosedPositions() throws Exception {
        mockMvc.perform(get("/api/futures/closed-positions")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    @DisplayName("GET /api/futures/closed-positions - 심볼 필터링")
    void getClosedPositionsWithSymbolFilter() throws Exception {
        mockMvc.perform(get("/api/futures/closed-positions")
                        .param("symbol", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].symbol").value("BTCUSDT"));
    }

    @Test
    @DisplayName("GET /api/futures/closed-positions - 방향 필터링")
    void getClosedPositionsWithSideFilter() throws Exception {
        mockMvc.perform(get("/api/futures/closed-positions")
                        .param("side", "LONG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }
}
