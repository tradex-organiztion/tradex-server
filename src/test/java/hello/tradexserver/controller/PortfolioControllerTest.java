package hello.tradexserver.controller;

import hello.tradexserver.domain.DailyStats;
import hello.tradexserver.domain.User;
import hello.tradexserver.repository.DailyStatsRepository;
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
import java.time.LocalDate;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 테스트 유저 생성
        testUser = userRepository.save(User.builder()
                .email("portfolio-test@test.com")
                .username("portfolioTestUser")
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

        // 테스트 데이터: 최근 7일 DailyStats
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            dailyStatsRepository.save(DailyStats.builder()
                    .user(testUser)
                    .statDate(today.minusDays(i))
                    .realizedPnl(new BigDecimal("100").multiply(BigDecimal.valueOf(7 - i)))
                    .winCount(3)
                    .lossCount(1)
                    .totalAsset(new BigDecimal("10000").add(new BigDecimal("100").multiply(BigDecimal.valueOf(7 - i))))
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/portfolio/summary - 포트폴리오 요약 조회")
    void getPortfolioSummary() throws Exception {
        mockMvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.todayPnl").exists())
                .andExpect(jsonPath("$.data.weeklyPnl").exists());
    }

    @Test
    @DisplayName("GET /api/portfolio/cumulative-profit - 누적 손익 조회")
    void getCumulativeProfit() throws Exception {
        mockMvc.perform(get("/api/portfolio/cumulative-profit")
                        .param("period", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalProfit").exists())
                .andExpect(jsonPath("$.data.dailyProfits").isArray());
    }

    @Test
    @DisplayName("GET /api/portfolio/asset-history - 월간 자산 추이 조회")
    void getAssetHistory() throws Exception {
        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/portfolio/asset-history")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.year").value(today.getYear()))
                .andExpect(jsonPath("$.data.month").value(today.getMonthValue()));
    }

    @Test
    @DisplayName("GET /api/portfolio/daily-profit - 일별 손익 조회")
    void getDailyProfit() throws Exception {
        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/portfolio/daily-profit")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.monthlyTotalPnl").exists())
                .andExpect(jsonPath("$.data.dailyPnlList").isArray());
    }
}
