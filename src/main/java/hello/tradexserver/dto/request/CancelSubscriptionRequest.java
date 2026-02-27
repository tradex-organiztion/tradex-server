package hello.tradexserver.dto.request;

import lombok.Getter;

@Getter
public class CancelSubscriptionRequest {

    private String reason;
}