package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.util.BinanceSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Slf4j
@RequiredArgsConstructor
public class BinanceRestClient implements ExchangeRestClient {

    private final String apiKey;
    private final String apiSecret;
    private final RestTemplate restTemplate;

    // Testnet API
    private static final String BASE_URL = "https://testnet.binancefuture.com";
    // Live API
    // private static final String BASE_URL = "https://fapi.binance.com";

    /**
     * User Data Stream listenKey 생성
     */
    public String createListenKey() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MBX-APIKEY", apiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + "/fapi/v1/listenKey";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class
            );

            String body = response.getBody();
            if (body != null && body.contains("listenKey")) {
                int start = body.indexOf("\"listenKey\":\"") + 13;
                int end = body.indexOf("\"", start);
                String listenKey = body.substring(start, end);
                log.info("[Binance] ListenKey 생성 완료");
                return listenKey;
            }
            return null;
        } catch (Exception e) {
            log.error("[Binance] ListenKey 생성 실패", e);
            throw new RuntimeException("Binance createListenKey 실패", e);
        }
    }

    /**
     * User Data Stream listenKey 연장
     */
    public void keepAliveListenKey() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MBX-APIKEY", apiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + "/fapi/v1/listenKey";

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            log.debug("[Binance] ListenKey 연장 완료");
        } catch (Exception e) {
            log.error("[Binance] ListenKey 연장 실패", e);
        }
    }

    /**
     * Position Information V3
     */
    public String getPositionRisk(String symbol) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String queryString = "timestamp=" + timestamp;
            if (symbol != null && !symbol.isEmpty()) {
                queryString += "&symbol=" + symbol;
            }

            String signature = BinanceSignatureUtil.generateSignature(apiSecret, queryString);
            queryString += "&signature=" + signature;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MBX-APIKEY", apiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + "/fapi/v3/positionRisk?" + queryString;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class
            );

            log.info("[Binance] Position Risk 응답");
            return response.getBody();
        } catch (Exception e) {
            log.error("[Binance] Position Risk 조회 실패", e);
            throw new RuntimeException("Binance getPositionRisk 실패", e);
        }
    }

    /**
     * Account Trade List (raw)
     */
    public String getUserTrades(String symbol, Integer limit, boolean latestFirst) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String queryString = "symbol=" + symbol + "&timestamp=" + timestamp;
            if (limit != null) {
                queryString += "&limit=" + limit;
            }

            String signature = BinanceSignatureUtil.generateSignature(apiSecret, queryString);
            queryString += "&signature=" + signature;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MBX-APIKEY", apiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + "/fapi/v1/userTrades?" + queryString;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("[Binance] User Trades 조회 실패", e);
            throw new RuntimeException("Binance getUserTrades 실패", e);
        }
    }

    @Override
    public BigDecimal getAsset(ExchangeApiKey apiKey) {
        // TODO: implement
        return null;
    }
}