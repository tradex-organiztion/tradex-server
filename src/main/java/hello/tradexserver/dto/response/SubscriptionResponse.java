package hello.tradexserver.dto.response;

import hello.tradexserver.domain.Subscription;
import hello.tradexserver.domain.enums.SubscriptionPlan;
import hello.tradexserver.domain.enums.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class SubscriptionResponse {

    private SubscriptionPlan currentPlan;
    private String displayName;
    private int price;
    private LocalDate nextBillingDate;
    private SubscriptionStatus status;
    private String cardNumber;   // 마스킹된 카드번호 (예: 4330-****-****-123*)
    private String cardCompany;

    public static SubscriptionResponse from(Subscription subscription) {
        return SubscriptionResponse.builder()
                .currentPlan(subscription.getPlan())
                .displayName(subscription.getPlan().getDisplayName())
                .price(subscription.getPlan().getPrice())
                .nextBillingDate(subscription.getNextBillingDate())
                .status(subscription.getStatus())
                .cardNumber(subscription.getCardNumber())
                .cardCompany(subscription.getCardCompany())
                .build();
    }

    public static SubscriptionResponse freeDefault() {
        return SubscriptionResponse.builder()
                .currentPlan(SubscriptionPlan.FREE)
                .displayName(SubscriptionPlan.FREE.getDisplayName())
                .price(SubscriptionPlan.FREE.getPrice())
                .nextBillingDate(null)
                .status(SubscriptionStatus.ACTIVE)
                .cardNumber(null)
                .cardCompany(null)
                .build();
    }
}