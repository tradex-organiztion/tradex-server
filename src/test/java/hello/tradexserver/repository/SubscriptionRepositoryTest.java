package hello.tradexserver.repository;

import hello.tradexserver.domain.Subscription;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.SubscriptionPlan;
import hello.tradexserver.domain.enums.SubscriptionStatus;
import hello.tradexserver.config.JpaConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class SubscriptionRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        // em.clear() 없이 persist만 - 이후 테스트에서 관리 상태(managed) 유지
        user = em.persist(User.builder()
                .email("sub-repo-test@test.com")
                .username("subRepoTestUser")
                .build());

        otherUser = em.persist(User.builder()
                .email("sub-repo-other@test.com")
                .username("subRepoOtherUser")
                .build());
    }

    private Subscription createSubscription(User owner, SubscriptionPlan plan,
                                            SubscriptionStatus status, LocalDate nextBillingDate) {
        return em.persist(Subscription.builder()
                .user(owner)
                .plan(plan)
                .status(status)
                .billingKey("billing-key-" + owner.getId())
                .customerKey("customer-key-" + owner.getId())
                .cardNumber("4330-****-****-1234")
                .cardCompany("현대")
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(nextBillingDate)
                .build());
    }

    // ===== findByUserId =====

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("구독이 있는 유저 조회 성공")
        void 구독이_있는_유저_조회_성공() {
            createSubscription(user, SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(20));
            em.flush();
            em.clear();

            Optional<Subscription> result = subscriptionRepository.findByUserId(user.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getPlan()).isEqualTo(SubscriptionPlan.PRO);
            assertThat(result.get().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(result.get().getCardNumber()).isEqualTo("4330-****-****-1234");
        }

        @Test
        @DisplayName("구독이 없는 유저 조회 시 빈 Optional 반환")
        void 구독이_없는_유저_조회_시_빈_Optional_반환() {
            em.flush();
            em.clear();

            Optional<Subscription> result = subscriptionRepository.findByUserId(user.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("다른 유저의 구독은 조회되지 않음")
        void 다른_유저의_구독은_조회되지_않음() {
            createSubscription(otherUser, SubscriptionPlan.PREMIUM, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(10));
            em.flush();
            em.clear();

            Optional<Subscription> result = subscriptionRepository.findByUserId(user.getId());

            assertThat(result).isEmpty();
        }
    }

    // ===== findByUser =====

    @Nested
    @DisplayName("findByUser")
    class FindByUser {

        @Test
        @DisplayName("User 엔티티로 구독 조회 성공")
        void User_엔티티로_구독_조회_성공() {
            createSubscription(user, SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(20));
            em.flush();
            em.clear();

            User managedUser = em.find(User.class, user.getId());
            Optional<Subscription> result = subscriptionRepository.findByUser(managedUser);

            assertThat(result).isPresent();
            assertThat(result.get().getUser().getId()).isEqualTo(user.getId());
        }
    }

    // ===== findByNextBillingDateAndStatus =====

    @Nested
    @DisplayName("findByNextBillingDateAndStatus")
    class FindByNextBillingDateAndStatus {

        @Test
        @DisplayName("오늘 결제일인 ACTIVE 구독 조회")
        void 오늘_결제일인_ACTIVE_구독_조회() {
            createSubscription(user, SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, LocalDate.now());
            createSubscription(otherUser, SubscriptionPlan.PREMIUM, SubscriptionStatus.ACTIVE, LocalDate.now());
            em.flush();
            em.clear();

            List<Subscription> result = subscriptionRepository
                    .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.ACTIVE);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(s -> s.getStatus() == SubscriptionStatus.ACTIVE);
            assertThat(result).allMatch(s -> s.getNextBillingDate().isEqual(LocalDate.now()));
        }

        @Test
        @DisplayName("결제일이 다른 구독은 조회되지 않음")
        void 결제일이_다른_구독은_조회되지_않음() {
            createSubscription(user, SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(1));
            em.flush();
            em.clear();

            List<Subscription> result = subscriptionRepository
                    .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.ACTIVE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("오늘 결제일이지만 CANCELED 상태면 ACTIVE 조회에 포함되지 않음")
        void 오늘_결제일이지만_CANCELED_상태면_ACTIVE_조회에_포함되지_않음() {
            createSubscription(user, SubscriptionPlan.PRO, SubscriptionStatus.CANCELED, LocalDate.now());
            em.flush();
            em.clear();

            List<Subscription> result = subscriptionRepository
                    .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.ACTIVE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CANCELED 상태로 조회 가능 (해지 예약 처리용)")
        void CANCELED_상태로_조회_가능() {
            createSubscription(user, SubscriptionPlan.PRO, SubscriptionStatus.CANCELED, LocalDate.now());
            em.flush();
            em.clear();

            List<Subscription> result = subscriptionRepository
                    .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.CANCELED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        }

        @Test
        @DisplayName("ACTIVE, CANCELED 혼재 시 지정한 상태만 조회")
        void ACTIVE_CANCELED_혼재_시_지정한_상태만_조회() {
            createSubscription(user, SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, LocalDate.now());
            createSubscription(otherUser, SubscriptionPlan.PRO, SubscriptionStatus.CANCELED, LocalDate.now());
            em.flush();
            em.clear();

            List<Subscription> activeResult = subscriptionRepository
                    .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.ACTIVE);
            List<Subscription> canceledResult = subscriptionRepository
                    .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.CANCELED);

            assertThat(activeResult).hasSize(1);
            assertThat(canceledResult).hasSize(1);
        }
    }

    // ===== save (상태 변경) =====

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("구독 정보 저장 성공")
        void 구독_정보_저장_성공() {
            Subscription saved = subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(SubscriptionPlan.PRO)
                    .status(SubscriptionStatus.ACTIVE)
                    .billingKey("billing-key-abc")
                    .customerKey("customer-key-abc")
                    .cardNumber("4330-****-****-1234")
                    .cardCompany("현대")
                    .startDate(LocalDate.now())
                    .nextBillingDate(LocalDate.now().plusMonths(1))
                    .build());
            em.flush();
            em.clear();

            Subscription found = em.find(Subscription.class, saved.getId());
            assertThat(found.getPlan()).isEqualTo(SubscriptionPlan.PRO);
            assertThat(found.getBillingKey()).isEqualTo("billing-key-abc");
            assertThat(found.getNextBillingDate()).isEqualTo(LocalDate.now().plusMonths(1));
        }

        @Test
        @DisplayName("구독 해지 후 상태 변경 저장")
        void 구독_해지_후_상태_변경_저장() {
            Subscription sub = createSubscription(user, SubscriptionPlan.PRO,
                    SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(10));
            em.flush();
            em.clear();

            Subscription found = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
            found.cancel("비용 부담");
            subscriptionRepository.save(found);
            em.flush();
            em.clear();

            Subscription updated = em.find(Subscription.class, sub.getId());
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            assertThat(updated.getCancellationReason()).isEqualTo("비용 부담");
            assertThat(updated.getCancelledAt()).isEqualTo(LocalDate.now());
        }
    }
}
