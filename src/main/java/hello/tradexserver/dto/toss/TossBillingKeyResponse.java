package hello.tradexserver.dto.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossBillingKeyResponse {

    private String billingKey;
    private String customerKey;
    private String cardCompany;
    private String cardNumber;
    private Card card;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {
        private String number;
        private String cardType;
        private String ownerType;
    }
}