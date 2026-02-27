package hello.tradexserver.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionPlan {
    FREE("무료", 0),
    PRO("프로", 29000),
    PREMIUM("프리미엄", 99000);

    private final String displayName;
    private final int price;
}
