package hello.tradexserver.dto.response;

import hello.tradexserver.domain.enums.SubscriptionPlan;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanInfoResponse {

    private SubscriptionPlan plan;
    private String displayName;
    private int price;
    private boolean isCurrent;

    public static PlanInfoResponse of(SubscriptionPlan plan, boolean isCurrent) {
        return PlanInfoResponse.builder()
                .plan(plan)
                .displayName(plan.getDisplayName())
                .price(plan.getPrice())
                .isCurrent(isCurrent)
                .build();
    }
}