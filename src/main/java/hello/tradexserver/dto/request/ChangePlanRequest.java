package hello.tradexserver.dto.request;

import hello.tradexserver.domain.enums.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ChangePlanRequest {

    @NotNull(message = "변경할 플랜을 선택해주세요")
    private SubscriptionPlan newPlan;
}