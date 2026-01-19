package hello.tradexserver.openApi;

import hello.tradexserver.domain.Order;
import hello.tradexserver.dto.response.BybitClosedPnlResponse;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.openApi.webSocket.PositionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ByBitExchangeService implements ExchangeService {

    private final String apiKey;
    private final String apiSecret;
    private final RestTemplate restTemplate;
    // test api
    private static final String BASE_URL = "https://api-testnet.bybit.com/v5";
    // live api
    //private static final String BASE_URL = "https://api.bybit.com/v5";

    @Override
    public List<PositionResponse> getPositions(int limit) {
        String recvWindow = "5000";
        String queryString = "category=linear&limit=" + limit;

        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = generateSignature(timestamp, recvWindow, queryString);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-BAPI-API-KEY", apiKey);
        headers.set("X-BAPI-TIMESTAMP", timestamp);
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-RECV-WINDOW", recvWindow);

        try {
            String url = BASE_URL + "/position/closed-pnl?" + queryString;
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BybitClosedPnlResponse> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    BybitClosedPnlResponse.class
            );

            BybitClosedPnlResponse response = responseEntity.getBody();

            if (response == null || response.getResult() == null || response.getResult().getList() == null) {
                log.info("바이비트 값 없음 {}", response != null ? response.getResult() : null);
                return List.of();
            }

            log.info("바이비트 응답 코드: {}", response.getRetCode());
            log.info("바이비트 값 {}", response.getResult());
            return List.of();
        } catch (Exception e) {
            throw new RuntimeException("Bybit getClosedPositions 실패", e);
        }
    }

    @Override
    public List<Order> getOrders() {
        // TODO: Bybit API 호출
        return List.of();
    }

    @Override
    public void subscribePositionUpdates(PositionListener listener) {
        // TODO: Bybit WebSocket 구독
    }

    private String generateSignature(String timestamp, String recvWindow, String queryString) {
        // Bybit v5 시그니처: timestamp + apiKey + recvWindow + queryString
        String payload = timestamp + apiKey + recvWindow + queryString;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(payload.getBytes());

            // Hex 인코딩 (Base64가 아님)
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("시그니처 생성 실패", e);
        }
    }
}
