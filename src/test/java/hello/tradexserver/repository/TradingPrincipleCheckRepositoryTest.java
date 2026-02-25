package hello.tradexserver.repository;

import hello.tradexserver.domain.*;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TradingPrincipleCheckRepository 테스트")
class TradingPrincipleCheckRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private TradingPrincipleCheckRepository tradingPrincipleCheckRepository;

    private User user;
    private TradingJournal journal1;
    private TradingJournal journal2;
    private TradingPrinciple principle1;
    private TradingPrinciple principle2;
    private TradingPrinciple principle3;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder()
                .email("check-repo-test@test.com")
                .username("checkRepoTestUser")
                .build());

        principle1 = em.persist(TradingPrinciple.builder()
                .user(user).content("손절 기준 2% 준수").build());
        principle2 = em.persist(TradingPrinciple.builder()
                .user(user).content("3연속 손실 시 거래 중단").build());
        principle3 = em.persist(TradingPrinciple.builder()
                .user(user).content("계획 외 진입 금지").build());

        Position pos1 = em.persist(Position.builder()
                .user(user).symbol("BTCUSDT").side(PositionSide.LONG)
                .avgEntryPrice(new BigDecimal("40000")).currentSize(BigDecimal.ZERO)
                .entryTime(LocalDateTime.now().minusHours(2)).exitTime(LocalDateTime.now())
                .status(PositionStatus.CLOSED).build());
        journal1 = em.persist(TradingJournal.builder()
                .user(user).position(pos1).build());

        Position pos2 = em.persist(Position.builder()
                .user(user).symbol("ETHUSDT").side(PositionSide.SHORT)
                .avgEntryPrice(new BigDecimal("2500")).currentSize(BigDecimal.ZERO)
                .entryTime(LocalDateTime.now().minusDays(1)).exitTime(LocalDateTime.now())
                .status(PositionStatus.CLOSED).build());
        journal2 = em.persist(TradingJournal.builder()
                .user(user).position(pos2).build());

        // journal1: principle1(체크), principle2(미체크)
        em.persist(TradingPrincipleCheck.builder()
                .tradingJournal(journal1).tradingPrinciple(principle1).isChecked(true).build());
        em.persist(TradingPrincipleCheck.builder()
                .tradingJournal(journal1).tradingPrinciple(principle2).isChecked(false).build());

        // journal2: principle1(미체크), principle3(체크)
        em.persist(TradingPrincipleCheck.builder()
                .tradingJournal(journal2).tradingPrinciple(principle1).isChecked(false).build());
        em.persist(TradingPrincipleCheck.builder()
                .tradingJournal(journal2).tradingPrinciple(principle3).isChecked(true).build());

        em.flush();
        em.clear();
    }

    // ─── findByTradingJournalId 테스트 ────────────────────────────────────────

    @Nested
    @DisplayName("findByTradingJournalId - 일지별 체크 조회")
    class FindByTradingJournalId {

        @Test
        @DisplayName("해당 일지의 체크 목록만 반환")
        void 해당_일지_체크_반환() {
            List<TradingPrincipleCheck> result =
                    tradingPrincipleCheckRepository.findByTradingJournalId(journal1.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(c -> c.getTradingPrinciple().getId())
                    .containsExactlyInAnyOrder(principle1.getId(), principle2.getId());
        }

        @Test
        @DisplayName("다른 일지의 체크는 반환하지 않음")
        void 다른_일지_체크_미포함() {
            List<TradingPrincipleCheck> result =
                    tradingPrincipleCheckRepository.findByTradingJournalId(journal1.getId());

            // journal2에 속한 principle3가 포함되면 안 됨
            assertThat(result).extracting(c -> c.getTradingPrinciple().getId())
                    .doesNotContain(principle3.getId());
        }

        @Test
        @DisplayName("isChecked 값이 정확히 조회됨")
        void isChecked_값_정확히_조회() {
            List<TradingPrincipleCheck> result =
                    tradingPrincipleCheckRepository.findByTradingJournalId(journal1.getId());

            boolean principle1Checked = result.stream()
                    .filter(c -> c.getTradingPrinciple().getId().equals(principle1.getId()))
                    .findFirst().map(TradingPrincipleCheck::isChecked).orElse(false);
            boolean principle2Checked = result.stream()
                    .filter(c -> c.getTradingPrinciple().getId().equals(principle2.getId()))
                    .findFirst().map(TradingPrincipleCheck::isChecked).orElse(true);

            assertThat(principle1Checked).isTrue();
            assertThat(principle2Checked).isFalse();
        }

        @Test
        @DisplayName("체크 기록 없는 일지는 빈 목록 반환")
        void 체크기록_없는_일지_빈목록() {
            Position pos3 = em.persist(Position.builder()
                    .user(user).symbol("SOLUSDT").side(PositionSide.LONG)
                    .avgEntryPrice(new BigDecimal("100")).currentSize(BigDecimal.ZERO)
                    .entryTime(LocalDateTime.now().minusHours(1)).exitTime(LocalDateTime.now())
                    .status(PositionStatus.CLOSED).build());
            TradingJournal emptyJournal = em.persist(TradingJournal.builder()
                    .user(user).position(pos3).build());
            em.flush();
            em.clear();

            List<TradingPrincipleCheck> result =
                    tradingPrincipleCheckRepository.findByTradingJournalId(emptyJournal.getId());

            assertThat(result).isEmpty();
        }
    }

    // ─── deleteByTradingJournalId 테스트 ─────────────────────────────────────

    @Nested
    @DisplayName("deleteByTradingJournalId - 일지 체크 전체 삭제")
    class DeleteByTradingJournalId {

        @Test
        @DisplayName("해당 일지의 체크가 전부 삭제됨")
        void 해당_일지_체크_전부_삭제() {
            tradingPrincipleCheckRepository.deleteByTradingJournalId(journal1.getId());
            em.flush();
            em.clear();

            List<TradingPrincipleCheck> result =
                    tradingPrincipleCheckRepository.findByTradingJournalId(journal1.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("다른 일지의 체크는 삭제되지 않음")
        void 다른_일지_체크_유지() {
            tradingPrincipleCheckRepository.deleteByTradingJournalId(journal1.getId());
            em.flush();
            em.clear();

            List<TradingPrincipleCheck> journal2Checks =
                    tradingPrincipleCheckRepository.findByTradingJournalId(journal2.getId());

            assertThat(journal2Checks).hasSize(2);
        }
    }
}