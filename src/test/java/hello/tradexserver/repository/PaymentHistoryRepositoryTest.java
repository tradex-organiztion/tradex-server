package hello.tradexserver.repository;

import hello.tradexserver.domain.PaymentHistory;
import hello.tradexserver.domain.PaymentStatus;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.SubscriptionPlan;
import hello.tradexserver.config.JpaConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class PaymentHistoryRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder()
                .email("payment-history-test@test.com")
                .username("paymentHistoryTestUser")
                .build());

        otherUser = em.persist(User.builder()
                .email("payment-history-other@test.com")
                .username("paymentHistoryOtherUser")
                .build());

        em.flush();
        em.clear();
    }

    private PaymentHistory createHistory(User owner, SubscriptionPlan plan,
                                         PaymentStatus status, LocalDateTime paidAt) {
        PaymentHistory history = PaymentHistory.builder()
                .user(owner)
                .plan(plan)
                .amount(plan.getPrice())
                .paidAt(paidAt)
                .status(status)
                .paymentKey("pay-key-" + System.nanoTime())
                .orderId("order-" + System.nanoTime())
                .build();
        return em.persist(history);
    }

    // ===== findByUserIdOrderByPaidAtDesc =====

    @Nested
    @DisplayName("findByUserIdOrderByPaidAtDesc")
    class FindByUserIdOrderByPaidAtDesc {

        @Test
        @DisplayName("결제 내역이 최신순으로 반환")
        void 결제_내역이_최신순으로_반환() {
            LocalDateTime base = LocalDateTime.now().minusDays(3);
            createHistory(user, SubscriptionPlan.PRO, PaymentStatus.COMPLETED, base);
            createHistory(user, SubscriptionPlan.PRO, PaymentStatus.COMPLETED, base.plusDays(1));
            createHistory(user, SubscriptionPlan.PREMIUM, PaymentStatus.COMPLETED, base.plusDays(2));
            em.flush();
            em.clear();

            List<PaymentHistory> result = paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(user.getId());

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
            assertThat(result.get(0).getPaidAt()).isAfter(result.get(1).getPaidAt());
            assertThat(result.get(1).getPaidAt()).isAfter(result.get(2).getPaidAt());
        }

        @Test
        @DisplayName("결제 내역이 없으면 빈 리스트 반환")
        void 결제_내역이_없으면_빈_리스트_반환() {
            List<PaymentHistory> result = paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(user.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("다른 유저의 결제 내역이 포함되지 않음")
        void 다른_유저의_결제_내역이_포함되지_않음() {
            createHistory(user, SubscriptionPlan.PRO, PaymentStatus.COMPLETED, LocalDateTime.now());
            createHistory(otherUser, SubscriptionPlan.PREMIUM, PaymentStatus.COMPLETED, LocalDateTime.now());
            em.flush();
            em.clear();

            List<PaymentHistory> result = paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(user.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUser().getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("실패 내역도 함께 조회됨")
        void 실패_내역도_함께_조회됨() {
            createHistory(user, SubscriptionPlan.PRO, PaymentStatus.COMPLETED, LocalDateTime.now().minusDays(1));
            createHistory(user, SubscriptionPlan.PRO, PaymentStatus.FAILED, LocalDateTime.now());
            em.flush();
            em.clear();

            List<PaymentHistory> result = paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(user.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.get(1).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("여러 플랜 결제 내역 모두 조회")
        void 여러_플랜_결제_내역_모두_조회() {
            LocalDateTime base = LocalDateTime.now().minusDays(2);
            createHistory(user, SubscriptionPlan.PRO, PaymentStatus.COMPLETED, base);
            createHistory(user, SubscriptionPlan.PREMIUM, PaymentStatus.COMPLETED, base.plusDays(1));
            createHistory(user, SubscriptionPlan.PRO, PaymentStatus.COMPLETED, base.plusDays(2));
            em.flush();
            em.clear();

            List<PaymentHistory> result = paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(user.getId());

            assertThat(result).hasSize(3);
            assertThat(result).extracting(PaymentHistory::getAmount)
                    .containsExactly(29000, 99000, 29000);
        }
    }

    // ===== save =====

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("결제 내역 저장 성공")
        void 결제_내역_저장_성공() {
            PaymentHistory history = PaymentHistory.builder()
                    .user(user)
                    .plan(SubscriptionPlan.PRO)
                    .amount(29000)
                    .paidAt(LocalDateTime.now())
                    .status(PaymentStatus.COMPLETED)
                    .paymentKey("pay-key-abc")
                    .orderId("order-abc")
                    .build();

            PaymentHistory saved = paymentHistoryRepository.save(history);
            em.flush();
            em.clear();

            PaymentHistory found = em.find(PaymentHistory.class, saved.getId());
            assertThat(found.getPlan()).isEqualTo(SubscriptionPlan.PRO);
            assertThat(found.getAmount()).isEqualTo(29000);
            assertThat(found.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(found.getPaymentKey()).isEqualTo("pay-key-abc");
        }

        @Test
        @DisplayName("FAILED 결제 내역 저장 성공")
        void FAILED_결제_내역_저장_성공() {
            PaymentHistory history = PaymentHistory.builder()
                    .user(user)
                    .plan(SubscriptionPlan.PREMIUM)
                    .amount(99000)
                    .paidAt(LocalDateTime.now())
                    .status(PaymentStatus.FAILED)
                    .build();

            PaymentHistory saved = paymentHistoryRepository.save(history);
            em.flush();
            em.clear();

            PaymentHistory found = em.find(PaymentHistory.class, saved.getId());
            assertThat(found.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(found.getPaymentKey()).isNull();
        }
    }
}