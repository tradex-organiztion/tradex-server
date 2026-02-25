package hello.tradexserver.dto.response;

import hello.tradexserver.domain.PaymentHistory;
import hello.tradexserver.domain.PaymentStatus;
import hello.tradexserver.domain.enums.SubscriptionPlan;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentHistoryResponse {

    private Long id;
    private SubscriptionPlan plan;
    private String planDisplayName;
    private int amount;
    private LocalDateTime paidAt;
    private PaymentStatus status;

    public static PaymentHistoryResponse from(PaymentHistory history) {
        return PaymentHistoryResponse.builder()
                .id(history.getId())
                .plan(history.getPlan())
                .planDisplayName(history.getPlan().getDisplayName())
                .amount(history.getAmount())
                .paidAt(history.getPaidAt())
                .status(history.getStatus())
                .build();
    }
}