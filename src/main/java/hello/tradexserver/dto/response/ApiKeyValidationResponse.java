package hello.tradexserver.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.enums.ExchangeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyValidationResponse {

    private Long apiKeyId;
    private ExchangeName exchangeName;
    @JsonProperty("isValid")
    private boolean isValid;
    private String maskedApiKey;

    public static ApiKeyValidationResponse of(ExchangeApiKey apiKey, boolean isValid) {
        return ApiKeyValidationResponse.builder()
                .apiKeyId(apiKey.getId())
                .exchangeName(apiKey.getExchangeName())
                .isValid(isValid)
                .maskedApiKey(maskApiKey(apiKey.getApiKey()))
                .build();
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
