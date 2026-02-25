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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final UserRepository userRepository;
    private final TossPaymentService tossPaymentService;

    // 현재 구독 조회 (없으면 FREE 기본값 반환)
    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription(Long userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(SubscriptionResponse::from)
                .orElse(SubscriptionResponse.freeDefault());
    }

    // 모든 요금제 리스트
    @Transactional(readOnly = true)
    public List<PlanInfoResponse> getAllPlans(Long userId) {
        SubscriptionPlan currentPlan = subscriptionRepository.findByUserId(userId)
                .map(Subscription::getPlan)
                .orElse(SubscriptionPlan.FREE);

        return Arrays.stream(SubscriptionPlan.values())
                .map(plan -> PlanInfoResponse.of(plan, plan == currentPlan))
                .collect(Collectors.toList());
    }

    // 결제 내역 조회
    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getPaymentHistory(Long userId) {
        return paymentHistoryRepository.findByUserIdOrderByPaidAtDesc(userId).stream()
                .map(PaymentHistoryResponse::from)
                .collect(Collectors.toList());
    }

    // 빌링키 발급 + 플랜 구독 시작 (최초 카드 등록)
    public SubscriptionResponse issueBillingKeyAndSubscribe(Long userId, BillingKeyIssueRequest request) {
        User user = findUser(userId);
        SubscriptionPlan plan = request.getPlan();

        if (plan == SubscriptionPlan.FREE) {
            throw new BusinessException(ErrorCode.FREE_PLAN_NO_BILLING);
        }

        // 토스 API로 빌링키 발급
        TossBillingKeyResponse billingKeyResponse = tossPaymentService.issueBillingKey(
                request.getAuthKey(), request.getCustomerKey()
        );

        String cardNumber = billingKeyResponse.getCardNumber();
        String cardCompany = billingKeyResponse.getCardCompany();

        // 기존 구독 or 신규 생성
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> Subscription.builder()
                        .user(user)
                        .startDate(LocalDate.now())
                        .build());

        subscription.updateBillingKey(
                billingKeyResponse.getBillingKey(),
                request.getCustomerKey(),
                cardNumber,
                cardCompany
        );
        subscription.updatePlan(plan);
        subscription.reactivate();

        // 즉시 첫 결제 실행
        TossPaymentResponse paymentResponse = chargeAndSaveHistory(subscription, plan, user);

        subscription.updateNextBillingDate(LocalDate.now().plusMonths(1));
        subscriptionRepository.save(subscription);

        log.info("빌링키 발급 및 구독 완료 - userId: {}, plan: {}, paymentKey: {}",
                userId, plan, paymentResponse.getPaymentKey());

        return SubscriptionResponse.from(subscription);
    }

    // 플랜 변경
    public SubscriptionResponse changePlan(Long userId, ChangePlanRequest request) {
        User user = findUser(userId);
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        SubscriptionPlan newPlan = request.getNewPlan();

        if (subscription.getPlan() == newPlan) {
            throw new BusinessException(ErrorCode.ALREADY_SAME_PLAN);
        }

        // 유료 플랜으로 변경 시 빌링키 필요
        if (newPlan != SubscriptionPlan.FREE && subscription.getBillingKey() == null) {
            throw new BusinessException(ErrorCode.BILLING_KEY_REQUIRED);
        }

        SubscriptionPlan prevPlan = subscription.getPlan();
        subscription.updatePlan(newPlan);

        if (newPlan == SubscriptionPlan.FREE) {
            // 무료 플랜으로 변경 = 해지와 동일 (다음 결제일까지 기존 서비스 유지 후 FREE 전환)
            subscription.cancel("플랜 무료 전환");
            log.info("플랜 무료 전환 예약 - userId: {}, prevPlan: {}", userId, prevPlan);
        } else {
            // 유료 플랜 간 변경: 즉시 결제 후 nextBillingDate 재설정
            chargeAndSaveHistory(subscription, newPlan, user);
            subscription.updateNextBillingDate(LocalDate.now().plusMonths(1));
            subscription.reactivate();
            log.info("플랜 변경 완료 - userId: {}, {} → {}", userId, prevPlan, newPlan);
        }

        subscriptionRepository.save(subscription);
        return SubscriptionResponse.from(subscription);
    }

    // 결제 수단 변경 (새 카드로 빌링키 재발급)
    public SubscriptionResponse changePaymentMethod(Long userId, PaymentMethodRequest request) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        TossBillingKeyResponse billingKeyResponse = tossPaymentService.issueBillingKey(
                request.getAuthKey(), request.getCustomerKey()
        );

        subscription.updateBillingKey(
                billingKeyResponse.getBillingKey(),
                request.getCustomerKey(),
                billingKeyResponse.getCardNumber(),
                billingKeyResponse.getCardCompany()
        );

        subscriptionRepository.save(subscription);
        log.info("결제 수단 변경 완료 - userId: {}", userId);
        return SubscriptionResponse.from(subscription);
    }

    // 구독 해지 (다음 결제일까지 유지, 이후 FREE 전환 예약)
    public SubscriptionResponse cancelSubscription(Long userId, CancelSubscriptionRequest request) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_ACTIVE);
        }

        subscription.cancel(request.getReason());
        subscriptionRepository.save(subscription);

        log.info("구독 해지 신청 완료 - userId: {}, reason: {}, nextBillingDate: {}",
                userId, request.getReason(), subscription.getNextBillingDate());

        return SubscriptionResponse.from(subscription);
    }

    // 빌링키로 결제 실행 + 내역 저장 (내부 공통 메서드)
    public TossPaymentResponse chargeAndSaveHistory(Subscription subscription, SubscriptionPlan plan, User user) {
        TossPaymentResponse paymentResponse;
        PaymentStatus paymentStatus;

        try {
            paymentResponse = tossPaymentService.chargeByBillingKey(
                    subscription.getBillingKey(),
                    subscription.getCustomerKey(),
                    plan.getPrice(),
                    plan.getDisplayName() + " 정기결제"
            );
            paymentStatus = PaymentStatus.COMPLETED;
        } catch (Exception e) {
            // 결제 실패 내역 저장 후 예외 재발생
            savePaymentHistory(user, plan, PaymentStatus.FAILED, null, null);
            throw e;
        }

        savePaymentHistory(user, plan, paymentStatus, paymentResponse.getPaymentKey(), paymentResponse.getOrderId());
        return paymentResponse;
    }

    private void savePaymentHistory(User user, SubscriptionPlan plan, PaymentStatus status,
                                    String paymentKey, String orderId) {
        PaymentHistory history = PaymentHistory.builder()
                .user(user)
                .plan(plan)
                .amount(plan.getPrice())
                .paidAt(LocalDateTime.now())
                .status(status)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .build();
        paymentHistoryRepository.save(history);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}