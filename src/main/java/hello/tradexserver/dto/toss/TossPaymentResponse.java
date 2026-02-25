package hello.tradexserver.dto.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentResponse {

    private String paymentKey;
    private String orderId;
    private String status;
    private int totalAmount;
    private String method;
    private String approvedAt;
}