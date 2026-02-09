package hello.tradexserver.dto.response;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.enums.ExchangeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeApiKeyResponse {

    private Long id;
    private ExchangeName exchangeName;
    private String maskedApiKey;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static ExchangeApiKeyResponse from(ExchangeApiKey apiKey) {
        return ExchangeApiKeyResponse.builder()
                .id(apiKey.getId())
                .exchangeName(apiKey.getExchangeName())
                .maskedApiKey(maskApiKey(apiKey.getApiKey()))
                .isActive(apiKey.getIsActive())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}