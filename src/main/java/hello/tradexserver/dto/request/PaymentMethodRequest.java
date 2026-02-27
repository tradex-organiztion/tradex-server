package hello.tradexserver.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class PaymentMethodRequest {

    @NotBlank(message = "authKey는 필수입니다")
    private String authKey;

    @NotBlank(message = "customerKey는 필수입니다")
    private String customerKey;
}