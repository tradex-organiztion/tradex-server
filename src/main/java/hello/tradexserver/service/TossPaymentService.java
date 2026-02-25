package hello.tradexserver.service;

import hello.tradexserver.dto.toss.TossBillingKeyResponse;
import hello.tradexserver.dto.toss.TossPaymentResponse;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TossPaymentService {

    private static final String TOSS_BASE_URL = "https://api.tosspayments.com";

    private final WebClient webClient;

    public TossPaymentService(@Value("${toss.secret-key}") String secretKey) {
        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.webClient = WebClient.builder()
                .baseUrl(TOSS_BASE_URL)
                .defaultHeader("Authorization", "Basic " + encoded)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // 빌링키 발급 (프론트에서 카드 인증 후 authKey + customerKey 전달받음)
    public TossBillingKeyResponse issueBillingKey(String authKey, String customerKey) {
        return webClient.post()
                .uri("/v1/billing/authorizations/issue")
                .bodyValue(Map.of(
                        "authKey", authKey,
                        "customerKey", customerKey
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class).map(body -> {
                            log.error("토스 빌링키 발급 실패: {}", body);
                            return new BusinessException(ErrorCode.PAYMENT_FAILED, "빌링키 발급에 실패했습니다: " + body);
                        })
                )
                .bodyToMono(TossBillingKeyResponse.class)
                .block();
    }

    // 빌링키로 자동결제 승인
    public TossPaymentResponse chargeByBillingKey(String billingKey, String customerKey,
                                                   int amount, String orderName) {
        String orderId = "tradex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        return webClient.post()
                .uri("/v1/billing/" + billingKey)
                .bodyValue(Map.of(
                        "customerKey", customerKey,
                        "amount", amount,
                        "orderId", orderId,
                        "orderName", orderName
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class).map(body -> {
                            log.error("토스 자동결제 실패: {}", body);
                            return new BusinessException(ErrorCode.PAYMENT_FAILED, "결제에 실패했습니다: " + body);
                        })
                )
                .bodyToMono(TossPaymentResponse.class)
                .block();
    }
}