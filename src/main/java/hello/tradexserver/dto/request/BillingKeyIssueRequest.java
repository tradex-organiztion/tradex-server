package hello.tradexserver.dto.request;

import hello.tradexserver.domain.enums.SubscriptionPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class BillingKeyIssueRequest {

    @NotBlank(message = "authKey는 필수입니다")
    private String authKey;

    @NotBlank(message = "customerKey는 필수입니다")
    private String customerKey;

    @NotNull(message = "플랜을 선택해주세요")
    private SubscriptionPlan plan;
}