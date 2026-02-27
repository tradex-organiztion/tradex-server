package hello.tradexserver.service;

import hello.tradexserver.domain.PaymentHistory;
import hello.tradexserver.domain.PaymentStatus;
import hello.tradexserver.domain.Subscription;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.SubscriptionPlan;
import hello.tradexserver.domain.enums.SubscriptionStatus;
import hello.tradexserver.dto.request.BillingKeyIssueRequest;
import hello.tradexserver.dto.request.CancelSubscriptionRequest;
import hello.tradexserver.dto.request.ChangePlanRequest;
import hello.tradexserver.dto.request.PaymentMethodRequest;
import hello.tradexserver.dto.response.PaymentHistoryResponse;
import hello.tradexserver.dto.response.PlanInfoResponse;
import hello.tradexserver.dto.response.SubscriptionResponse;
import hello.tradexserver.dto.toss.TossBillingKeyResponse;
import hello.tradexserver.dto.toss.TossPaymentResponse;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.PaymentHistoryRepository;
import hello.tradexserver.repository.SubscriptionRepository;
import hello.tradexserver.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TossPaymentService tossPaymentService;

    private User user;
    private Subscription proSubscription;
    private Subscription cancelledSubscription;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@test.com")
                .username("testuser")
                .build();

        proSubscription = Subscription.builder()
                .id(1L)
                .user(user)
                .plan(SubscriptionPlan.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .billingKey("test-billing-key")
                .customerKey("test-customer-key")
                .cardNumber("4330-****-****-1234")
                .cardCompany("현대")
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(20))
                .build();

        cancelledSubscription = Subscription.builder()
                .id(2L)
                .user(user)
                .plan(SubscriptionPlan.PRO)
                .status(SubscriptionStatus.CANCELED)
                .billingKey("test-billing-key")
                .customerKey("test-customer-key")
                .cardNumber("4330-****-****-1234")
                .cardCompany("현대")
                .startDate(LocalDate.now().minusMonths(2))
                .nextBillingDate(LocalDate.now().plusDays(5))
                .build();
    }

    // ===== 공통 헬퍼 =====

    private TossBillingKeyResponse mockBillingKeyResponse() {
        TossBillingKeyResponse response = mock(TossBillingKeyResponse.class);
        given(response.getBillingKey()).willReturn("new-billing-key");
        given(response.getCardNumber()).willReturn("4330-****-****-5678");
        given(response.getCardCompany()).willReturn("삼성");
        return response;
    }

    private TossPaymentResponse mockPaymentResponse() {
        TossPaymentResponse response = mock(TossPaymentResponse.class);
        given(response.getPaymentKey()).willReturn("pay-key-123");
        given(response.getOrderId()).willReturn("order-id-123");
        return response;
    }

    // ===== getMySubscription =====

    @Nested
    @DisplayName("getMySubscription")
    class GetMySubscription {

        @Test
        @DisplayName("구독이 있으면 구독 정보 반환")
        void 구독이_있으면_구독_정보_반환() {
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));

            SubscriptionResponse response = subscriptionService.getMySubscription(1L);

            assertThat(response.getCurrentPlan()).isEqualTo(SubscriptionPlan.PRO);
            assertThat(response.getDisplayName()).isEqualTo("프로");
            assertThat(response.getPrice()).isEqualTo(29000);
            assertThat(response.getCardNumber()).isEqualTo("4330-****-****-1234");
            assertThat(response.getCardCompany()).isEqualTo("현대");
            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("구독이 없으면 FREE 기본값 반환")
        void 구독이_없으면_FREE_기본값_반환() {
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());

            SubscriptionResponse response = subscriptionService.getMySubscription(1L);

            assertThat(response.getCurrentPlan()).isEqualTo(SubscriptionPlan.FREE);
            assertThat(response.getPrice()).isEqualTo(0);
            assertThat(response.getCardNumber()).isNull();
            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }
    }

    // ===== getAllPlans =====

    @Nested
    @DisplayName("getAllPlans")
    class GetAllPlans {

        @Test
        @DisplayName("요금제 3개 반환, 현재 플랜 표시")
        void 요금제_3개_반환_현재_플랜_표시() {
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));

            List<PlanInfoResponse> plans = subscriptionService.getAllPlans(1L);

            assertThat(plans).hasSize(3);
            assertThat(plans.stream().filter(PlanInfoResponse::isCurrent).count()).isEqualTo(1);
            assertThat(plans.stream()
                    .filter(p -> p.getPlan() == SubscriptionPlan.PRO)
                    .findFirst()
                    .map(PlanInfoResponse::isCurrent))
                    .hasValue(true);
        }

        @Test
        @DisplayName("구독이 없으면 FREE를 현재 플랜으로 표시")
        void 구독이_없으면_FREE를_현재_플랜으로_표시() {
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());

            List<PlanInfoResponse> plans = subscriptionService.getAllPlans(1L);

            assertThat(plans).hasSize(3);
            assertThat(plans.stream()
                    .filter(p -> p.getPlan() == SubscriptionPlan.FREE)
                    .findFirst()
                    .map(PlanInfoResponse::isCurrent))
                    .hasValue(true);
        }

        @Test
        @DisplayName("모든 요금제 금액 확인")
        void 모든_요금제_금액_확인() {
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());

            List<PlanInfoResponse> plans = subscriptionService.getAllPlans(1L);

            assertThat(plans).extracting(PlanInfoResponse::getPrice)
                    .containsExactlyInAnyOrder(0, 29000, 99000);
        }
    }

    // ===== getPaymentHistory =====

    @Nested
    @DisplayName("getPaymentHistory")
    class GetPaymentHistory {

        @Test
        @DisplayName("결제 내역 목록 반환")
        void 결제_내역_목록_반환() {
            PaymentHistory history = PaymentHistory.builder()
                    .id(1L)
                    .user(user)
                    .plan(SubscriptionPlan.PRO)
                    .amount(29000)
                    .paidAt(LocalDateTime.now())
                    .status(PaymentStatus.COMPLETED)
                    .paymentKey("pay-key-123")
                    .orderId("order-id-123")
                    .build();
            given(paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(1L)).willReturn(List.of(history));

            List<PaymentHistoryResponse> result = subscriptionService.getPaymentHistory(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlan()).isEqualTo(SubscriptionPlan.PRO);
            assertThat(result.get(0).getAmount()).isEqualTo(29000);
            assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.get(0).getPlanDisplayName()).isEqualTo("프로");
        }

        @Test
        @DisplayName("결제 내역 없으면 빈 리스트 반환")
        void 결제_내역_없으면_빈_리스트_반환() {
            given(paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(1L)).willReturn(List.of());

            List<PaymentHistoryResponse> result = subscriptionService.getPaymentHistory(1L);

            assertThat(result).isEmpty();
        }
    }

    // ===== issueBillingKeyAndSubscribe =====

    @Nested
    @DisplayName("issueBillingKeyAndSubscribe")
    class IssueBillingKeyAndSubscribe {

        @Test
        @DisplayName("신규 사용자 빌링키 발급 및 구독 성공")
        void 신규_사용자_빌링키_발급_및_구독_성공() {
            BillingKeyIssueRequest request = mock(BillingKeyIssueRequest.class);
            given(request.getAuthKey()).willReturn("auth-key");
            given(request.getCustomerKey()).willReturn("customer-key");
            given(request.getPlan()).willReturn(SubscriptionPlan.PRO);

            TossBillingKeyResponse billingKeyResp = mockBillingKeyResponse();
            TossPaymentResponse paymentResp = mockPaymentResponse();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(tossPaymentService.issueBillingKey("auth-key", "customer-key")).willReturn(billingKeyResp);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(tossPaymentService.chargeByBillingKey(any(), any(), anyInt(), any())).willReturn(paymentResp);
            given(paymentHistoryRepository.save(any())).willReturn(mock(PaymentHistory.class));
            given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.issueBillingKeyAndSubscribe(1L, request);

            assertThat(response.getCurrentPlan()).isEqualTo(SubscriptionPlan.PRO);
            assertThat(response.getCardNumber()).isEqualTo("4330-****-****-5678");
            assertThat(response.getCardCompany()).isEqualTo("삼성");
            assertThat(response.getNextBillingDate()).isEqualTo(LocalDate.now().plusMonths(1));
            verify(tossPaymentService).issueBillingKey("auth-key", "customer-key");
            verify(tossPaymentService).chargeByBillingKey(eq("new-billing-key"), eq("customer-key"), eq(29000), any());
        }

        @Test
        @DisplayName("기존 구독이 있으면 업데이트 후 결제")
        void 기존_구독이_있으면_업데이트_후_결제() {
            BillingKeyIssueRequest request = mock(BillingKeyIssueRequest.class);
            given(request.getAuthKey()).willReturn("auth-key");
            given(request.getCustomerKey()).willReturn("customer-key");
            given(request.getPlan()).willReturn(SubscriptionPlan.PREMIUM);

            TossBillingKeyResponse billingKeyResp = mockBillingKeyResponse();
            TossPaymentResponse paymentResp = mockPaymentResponse();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(tossPaymentService.issueBillingKey(any(), any())).willReturn(billingKeyResp);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));
            given(tossPaymentService.chargeByBillingKey(any(), any(), anyInt(), any())).willReturn(paymentResp);
            given(paymentHistoryRepository.save(any())).willReturn(mock(PaymentHistory.class));
            given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.issueBillingKeyAndSubscribe(1L, request);

            assertThat(response.getCurrentPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
            verify(tossPaymentService).chargeByBillingKey(any(), any(), eq(99000), any());
        }

        @Test
        @DisplayName("FREE 플랜 선택 시 예외 발생")
        void FREE_플랜_선택_시_예외_발생() {
            BillingKeyIssueRequest request = mock(BillingKeyIssueRequest.class);
            given(request.getPlan()).willReturn(SubscriptionPlan.FREE);

            assertThatThrownBy(() -> subscriptionService.issueBillingKeyAndSubscribe(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FREE_PLAN_NO_BILLING);

            verify(tossPaymentService, never()).issueBillingKey(any(), any());
        }

        @Test
        @DisplayName("결제 실패 시 FAILED 내역 저장 후 예외 발생")
        void 결제_실패_시_FAILED_내역_저장_후_예외_발생() {
            BillingKeyIssueRequest request = mock(BillingKeyIssueRequest.class);
            given(request.getAuthKey()).willReturn("auth-key");
            given(request.getCustomerKey()).willReturn("customer-key");
            given(request.getPlan()).willReturn(SubscriptionPlan.PRO);

            TossBillingKeyResponse billingKeyResp = mockBillingKeyResponse();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(tossPaymentService.issueBillingKey(any(), any())).willReturn(billingKeyResp);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(tossPaymentService.chargeByBillingKey(any(), any(), anyInt(), any()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));
            given(paymentHistoryRepository.save(any())).willReturn(mock(PaymentHistory.class));

            assertThatThrownBy(() -> subscriptionService.issueBillingKeyAndSubscribe(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_FAILED);

            // 실패 내역 저장 확인
            verify(paymentHistoryRepository).save(argThat(h -> h.getStatus() == PaymentStatus.FAILED));
        }
    }

    // ===== changePlan =====

    @Nested
    @DisplayName("changePlan")
    class ChangePlan {

        @BeforeEach
        void mockUser() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
        }

        @Test
        @DisplayName("PRO → PREMIUM 업그레이드 성공")
        void PRO에서_PREMIUM으로_업그레이드_성공() {
            ChangePlanRequest request = mock(ChangePlanRequest.class);
            given(request.getNewPlan()).willReturn(SubscriptionPlan.PREMIUM);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));
            TossPaymentResponse paymentResp = mockPaymentResponse();
            given(tossPaymentService.chargeByBillingKey(any(), any(), anyInt(), any())).willReturn(paymentResp);
            given(paymentHistoryRepository.save(any())).willReturn(mock(PaymentHistory.class));
            given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.changePlan(1L, request);

            assertThat(response.getCurrentPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(tossPaymentService).chargeByBillingKey(any(), any(), eq(99000), any());
        }

        @Test
        @DisplayName("동일한 플랜으로 변경 시 예외 발생")
        void 동일한_플랜으로_변경_시_예외_발생() {
            ChangePlanRequest request = mock(ChangePlanRequest.class);
            given(request.getNewPlan()).willReturn(SubscriptionPlan.PRO);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));

            assertThatThrownBy(() -> subscriptionService.changePlan(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_SAME_PLAN);

            verify(tossPaymentService, never()).chargeByBillingKey(any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("빌링키 없이 유료 플랜 변경 시 예외 발생")
        void 빌링키_없이_유료_플랜_변경_시_예외_발생() {
            ChangePlanRequest request = mock(ChangePlanRequest.class);
            given(request.getNewPlan()).willReturn(SubscriptionPlan.PRO);

            Subscription freeSubscription = Subscription.builder()
                    .id(3L)
                    .user(user)
                    .plan(SubscriptionPlan.FREE)
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(freeSubscription));

            assertThatThrownBy(() -> subscriptionService.changePlan(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BILLING_KEY_REQUIRED);
        }

        @Test
        @DisplayName("FREE 플랜으로 변경 시 CANCELED 처리 (해지 예약)")
        void FREE_플랜으로_변경_시_CANCELED_처리() {
            ChangePlanRequest request = mock(ChangePlanRequest.class);
            given(request.getNewPlan()).willReturn(SubscriptionPlan.FREE);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));
            given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.changePlan(1L, request);

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            verify(tossPaymentService, never()).chargeByBillingKey(any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("구독이 없으면 예외 발생")
        void 구독이_없으면_예외_발생() {
            ChangePlanRequest request = mock(ChangePlanRequest.class);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.changePlan(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
    }

    // ===== changePaymentMethod =====

    @Nested
    @DisplayName("changePaymentMethod")
    class ChangePaymentMethod {

        @Test
        @DisplayName("결제 수단 변경 성공")
        void 결제_수단_변경_성공() {
            PaymentMethodRequest request = mock(PaymentMethodRequest.class);
            given(request.getAuthKey()).willReturn("new-auth-key");
            given(request.getCustomerKey()).willReturn("new-customer-key");
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));
            TossBillingKeyResponse billingKeyResp = mockBillingKeyResponse();
            given(tossPaymentService.issueBillingKey("new-auth-key", "new-customer-key")).willReturn(billingKeyResp);
            given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.changePaymentMethod(1L, request);

            assertThat(response.getCardNumber()).isEqualTo("4330-****-****-5678");
            assertThat(response.getCardCompany()).isEqualTo("삼성");
            verify(tossPaymentService).issueBillingKey("new-auth-key", "new-customer-key");
            verify(subscriptionRepository).save(any(Subscription.class));
        }

        @Test
        @DisplayName("구독이 없으면 예외 발생")
        void 구독이_없으면_예외_발생() {
            PaymentMethodRequest request = mock(PaymentMethodRequest.class);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.changePaymentMethod(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
    }

    // ===== cancelSubscription =====

    @Nested
    @DisplayName("cancelSubscription")
    class CancelSubscription {

        @Test
        @DisplayName("구독 해지 성공 - CANCELED 상태로 변경")
        void 구독_해지_성공() {
            CancelSubscriptionRequest request = mock(CancelSubscriptionRequest.class);
            given(request.getReason()).willReturn("서비스 불만족");
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));
            given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.cancelSubscription(1L, request);

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            assertThat(proSubscription.getCancellationReason()).isEqualTo("서비스 불만족");
            assertThat(proSubscription.getCancelledAt()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("해지 후 다음 결제일은 그대로 유지")
        void 해지_후_다음_결제일은_그대로_유지() {
            LocalDate originalNextBillingDate = proSubscription.getNextBillingDate();
            CancelSubscriptionRequest request = mock(CancelSubscriptionRequest.class);
            given(request.getReason()).willReturn("비용 부담");
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(proSubscription));
            given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.cancelSubscription(1L, request);

            assertThat(response.getNextBillingDate()).isEqualTo(originalNextBillingDate);
        }

        @Test
        @DisplayName("이미 해지된 구독 재해지 시 예외 발생")
        void 이미_해지된_구독_재해지_시_예외_발생() {
            CancelSubscriptionRequest request = mock(CancelSubscriptionRequest.class);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.of(cancelledSubscription));

            assertThatThrownBy(() -> subscriptionService.cancelSubscription(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBSCRIPTION_NOT_ACTIVE);
        }

        @Test
        @DisplayName("구독이 없으면 예외 발생")
        void 구독이_없으면_예외_발생() {
            CancelSubscriptionRequest request = mock(CancelSubscriptionRequest.class);
            given(subscriptionRepository.findByUserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.cancelSubscription(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
    }
}