package hello.tradexserver.openApi.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.dto.BinanceAllOrderItem;
import hello.tradexserver.openApi.rest.dto.BinancePositionRisk;
import hello.tradexserver.openApi.rest.dto.BinanceUserTrade;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;
import hello.tradexserver.openApi.util.BinanceSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceRestClient implements ExchangeRestClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Testnet API
    private static final String BASE_URL = "https://testnet.binancefuture.com";
    // Live API
    // private static final String BASE_URL = "https://fapi.binance.com";

    /**
     * User Data Stream listenKey 생성
     */
    public String createListenKey(ExchangeApiKey apiKey) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/fapi/v1/listenKey", HttpMethod.POST, entity, String.class);

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
    public void keepAliveListenKey(ExchangeApiKey apiKey) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            restTemplate.exchange(
                    BASE_URL + "/fapi/v1/listenKey", HttpMethod.PUT, entity, String.class);
            log.debug("[Binance] ListenKey 연장 완료");
        } catch (Exception e) {
            log.error("[Binance] ListenKey 연장 실패", e);
        }
    }

    /**
     * Position Risk 조회 (leverage, open positions)
     */
    public List<BinancePositionRisk> fetchPositionRisk(ExchangeApiKey apiKey, String symbol) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            StringBuilder qs = new StringBuilder("timestamp=").append(timestamp);
            if (symbol != null && !symbol.isEmpty()) {
                qs.append("&symbol=").append(symbol);
            }
            String queryString = qs.toString();
            String signature = BinanceSignatureUtil.generateSignature(apiKey.getApiSecret(), queryString);
            queryString += "&signature=" + signature;

            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/fapi/v2/positionRisk?" + queryString,
                    HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return List.of();

            return objectMapper.readValue(body, new TypeReference<List<BinancePositionRisk>>() {});
        } catch (Exception e) {
            log.error("[Binance] Position Risk 조회 실패 - apiKeyId: {}", apiKey.getId(), e);
            return List.of();
        }
    }

    /**
     * 전체 오더 조회 (symbol 필수)
     */
    public List<BinanceAllOrderItem> fetchAllOrders(ExchangeApiKey apiKey, String symbol,
                                                     Long startTime, Long endTime) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            StringBuilder qs = new StringBuilder("symbol=").append(symbol)
                    .append("&timestamp=").append(timestamp);
            if (startTime != null) qs.append("&startTime=").append(startTime);
            if (endTime != null) qs.append("&endTime=").append(endTime);
            qs.append("&limit=100");
            String queryString = qs.toString();
            String signature = BinanceSignatureUtil.generateSignature(apiKey.getApiSecret(), queryString);
            queryString += "&signature=" + signature;

            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/fapi/v1/allOrders?" + queryString,
                    HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return List.of();

            return objectMapper.readValue(body, new TypeReference<List<BinanceAllOrderItem>>() {});
        } catch (Exception e) {
            log.error("[Binance] allOrders 조회 실패 - apiKeyId: {}, symbol: {}", apiKey.getId(), symbol, e);
            return List.of();
        }
    }

    /**
     * 유저 트레이드 조회 (수수료 보완용, symbol 필수)
     */
    public List<BinanceUserTrade> fetchUserTrades(ExchangeApiKey apiKey, String symbol,
                                                   Long startTime, Long endTime) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            StringBuilder qs = new StringBuilder("symbol=").append(symbol)
                    .append("&timestamp=").append(timestamp);
            if (startTime != null) qs.append("&startTime=").append(startTime);
            if (endTime != null) qs.append("&endTime=").append(endTime);
            qs.append("&limit=1000");
            String queryString = qs.toString();
            String signature = BinanceSignatureUtil.generateSignature(apiKey.getApiSecret(), queryString);
            queryString += "&signature=" + signature;

            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/fapi/v1/userTrades?" + queryString,
                    HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return List.of();

            return objectMapper.readValue(body, new TypeReference<List<BinanceUserTrade>>() {});
        } catch (Exception e) {
            log.error("[Binance] userTrades 조회 실패 - apiKeyId: {}, symbol: {}", apiKey.getId(), symbol, e);
            return List.of();
        }
    }

    @Override
    public BigDecimal getAsset(ExchangeApiKey apiKey) {
        // TODO: implement
        return null;
    }

    @Override
    public WalletBalanceResponse getWalletBalance(ExchangeApiKey apiKey) {
        // TODO: implement
        return null;
    }

    private HttpHeaders createApiKeyHeader(ExchangeApiKey apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey.getApiKey());
        return headers;
    }
}