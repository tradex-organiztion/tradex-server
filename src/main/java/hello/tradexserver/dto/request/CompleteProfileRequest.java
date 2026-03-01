package hello.tradexserver.dto.request;

import hello.tradexserver.domain.enums.ExchangeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProfileRequest {

    @Size(min = 2, max = 100, message = "사용자명은 2-100자 사이여야 합니다")
    private String username;

    @NotNull(message = "거래소 이름은 필수입니다")
    private ExchangeName exchangeName;

    @NotBlank(message = "API Key는 필수입니다")
    private String apiKey;

    @NotBlank(message = "API Secret은 필수입니다")
    private String apiSecret;

    private String passphrase; // Bitget 전용
}
