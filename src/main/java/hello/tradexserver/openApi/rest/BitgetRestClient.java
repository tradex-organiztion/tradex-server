package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.util.BitgetSignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Slf4j
public class BitgetRestClient implements ExchangeRestClient {

    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;
    private final RestTemplate restTemplate;
    private final boolean isDemoMode;

    private static final String BASE_URL = "https://api.bitget.com";

    // 프로덕션 모드 생성자
    public BitgetRestClient(String apiKey, String apiSecret, String passphrase, RestTemplate restTemplate) {
        this(apiKey, apiSecret, passphrase, restTemplate, false);
    }

    // 데모 모드 지원 생성자
    public BitgetRestClient(String apiKey, String apiSecret, String passphrase, RestTemplate restTemplate, boolean isDemoMode) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.passphrase = passphrase;
        this.restTemplate = restTemplate;
        this.isDemoMode = isDemoMode;
    }

    @Override
    public BigDecimal getAsset(ExchangeApiKey apiKey) {
        // TODO: implement
        log.info("[Bitget] getAsset - TODO");
        return null;
    }

    protected HttpHeaders createAuthHeaders(String timestamp, String method,
                                            String requestPath, String body) throws Exception {
        String sign = BitgetSignatureUtil.generateSignature(
                apiSecret, timestamp, method, requestPath, body);

        HttpHeaders headers = new HttpHeaders();
        headers.set("ACCESS-KEY", apiKey);
        headers.set("ACCESS-SIGN", sign);
        headers.set("ACCESS-TIMESTAMP", timestamp);
        headers.set("ACCESS-PASSPHRASE", passphrase);
        headers.set("Content-Type", "application/json");
        headers.set("locale", "en-US");

        if (isDemoMode) {
            headers.set("paptrading", "1");
        }

        return headers;
    }
}