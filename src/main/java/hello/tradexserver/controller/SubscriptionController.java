package hello.tradexserver.controller;

import hello.tradexserver.dto.request.BillingKeyIssueRequest;
import hello.tradexserver.dto.request.CancelSubscriptionRequest;
import hello.tradexserver.dto.request.ChangePlanRequest;
import hello.tradexserver.dto.request.PaymentMethodRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.PaymentHistoryResponse;
import hello.tradexserver.dto.response.PlanInfoResponse;
import hello.tradexserver.dto.response.SubscriptionResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscription", description = "구독 및 결제 관리")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "현재 구독 조회", description = "현재 구독 플랜 및 결제 수단 정보를 조회합니다")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMySubscription(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SubscriptionResponse response = subscriptionService.getMySubscription(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "요금제 목록 조회", description = "모든 요금제 리스트와 현재 플랜 정보를 반환합니다")
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanInfoResponse>>> getAllPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<PlanInfoResponse> response = subscriptionService.getAllPlans(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "결제 내역 조회", description = "결제 내역 목록을 조회합니다")
    @GetMapping("/payment-history")
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPaymentHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<PaymentHistoryResponse> response = subscriptionService.getPaymentHistory(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "빌링키 발급 및 구독 시작",
            description = "프론트에서 Toss SDK 카드 인증 후 authKey+customerKey를 받아 빌링키를 발급하고 구독을 시작합니다")
    @PostMapping("/billing-key")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> issueBillingKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody BillingKeyIssueRequest request
    ) {
        log.info("빌링키 발급 요청 - userId: {}, plan: {}", userDetails.getUserId(), request.getPlan());
        SubscriptionResponse response = subscriptionService.issueBillingKeyAndSubscribe(
                userDetails.getUserId(), request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "플랜 변경", description = "구독 플랜을 변경합니다")
    @PostMapping("/change")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> changePlan(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePlanRequest request
    ) {
        log.info("플랜 변경 요청 - userId: {}, newPlan: {}", userDetails.getUserId(), request.getNewPlan());
        SubscriptionResponse response = subscriptionService.changePlan(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "결제 수단 변경", description = "새 카드로 빌링키를 재발급하여 결제 수단을 변경합니다")
    @PostMapping("/payment-method")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> changePaymentMethod(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PaymentMethodRequest request
    ) {
        log.info("결제 수단 변경 요청 - userId: {}", userDetails.getUserId());
        SubscriptionResponse response = subscriptionService.changePaymentMethod(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "구독 해지",
            description = "구독을 해지합니다. 다음 결제일까지 현재 플랜 유지 후 FREE로 전환됩니다")
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancelSubscription(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CancelSubscriptionRequest request
    ) {
        log.info("구독 해지 요청 - userId: {}, reason: {}", userDetails.getUserId(), request.getReason());
        SubscriptionResponse response = subscriptionService.cancelSubscription(
                userDetails.getUserId(), request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}