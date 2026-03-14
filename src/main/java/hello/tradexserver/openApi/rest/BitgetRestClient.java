package hello.tradexserver.openApi.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.dto.BitgetOrderHistoryItem;
import hello.tradexserver.openApi.rest.dto.BitgetPositionItem;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;
import hello.tradexserver.config.ExchangeProperties;
import hello.tradexserver.openApi.util.BitgetSignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BitgetRestClient implements ExchangeRestClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean demoMode;

    public BitgetRestClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                             ExchangeProperties props) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = props.getBitget().getRestUrl();
        this.demoMode = props.getBitget().isDemoMode();
    }

    private static final List<String> PRODUCT_TYPES = List.of("USDT-FUTURES", "USDC-FUTURES");

    @Override
    public boolean validateApiKey(ExchangeApiKey apiKey) {
        try {
            String requestPath = "/api/v2/mix/account/accounts";
            String queryString = "productType=USDT-FUTURES";
            String fullPath = requestPath + "?" + queryString;

            String timestamp = String.valueOf(System.currentTimeMillis());
            HttpHeaders headers = createAuthHeaders(apiKey, timestamp, "GET", fullPath, "");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + fullPath, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return false;

            JsonNode root = objectMapper.readTree(body);
            return "00000".equals(root.path("code").asText());
        } catch (Exception e) {
            log.warn("[Bitget] API Key 검증 실패 - apiKeyId: {}", apiKey.getId(), e);
            return false;
        }
    }

    @Override
    public BigDecimal getAsset(ExchangeApiKey apiKey) {
        // TODO: implement
        log.info("[Bitget] getAsset - TODO");
        return null;
    }

    @Override
    public WalletBalanceResponse getWalletBalance(ExchangeApiKey apiKey) {
        // TODO: implement
        return null;
    }

    /**
     * 오더 히스토리 조회
     * GET /api/v2/mix/order/orders-history
     */
    public List<BitgetOrderHistoryItem> fetchOrderHistory(ExchangeApiKey apiKey, String symbol,
                                                           Long startTime, Long endTime) {
        List<BitgetOrderHistoryItem> allOrders = new ArrayList<>();
        for (String productType : PRODUCT_TYPES) {
            allOrders.addAll(fetchOrderHistoryByProductType(apiKey, symbol, startTime, endTime, productType));
        }
        return allOrders;
    }

    private List<BitgetOrderHistoryItem> fetchOrderHistoryByProductType(ExchangeApiKey apiKey, String symbol,
                                                                         Long startTime, Long endTime,
                                                                         String productType) {
        try {
            String requestPath = "/api/v2/mix/order/orders-history";
            StringBuilder qs = new StringBuilder("productType=").append(productType);
            if (symbol != null && !symbol.isEmpty()) {
                qs.append("&symbol=").append(symbol);
            }
            if (startTime != null) qs.append("&startTime=").append(startTime);
            if (endTime != null) qs.append("&endTime=").append(endTime);
            qs.append("&limit=100");

            String queryString = qs.toString();
            String fullPath = requestPath + "?" + queryString;

            String timestamp = String.valueOf(System.currentTimeMillis());
            HttpHeaders headers = createAuthHeaders(apiKey, timestamp, "GET", fullPath, "");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + fullPath, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return List.of();

            JsonNode root = objectMapper.readTree(body);
            if (!"00000".equals(root.path("code").asText())) {
                log.error("[Bitget] fetchOrderHistory({}) 실패 - code: {}, msg: {}",
                        productType, root.path("code").asText(), root.path("msg").asText());
                return List.of();
            }

            JsonNode orderList = root.path("data").path("orderList");
            if (orderList.isMissingNode() || !orderList.isArray()) return List.of();

            return objectMapper.readValue(
                    orderList.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BitgetOrderHistoryItem.class)
            );
        } catch (Exception e) {
            log.error("[Bitget] fetchOrderHistory({}) 실패 - apiKeyId: {}", productType, apiKey.getId(), e);
            return List.of();
        }
    }

    /**
     * 전체 포지션 조회
     * GET /api/v2/mix/position/all-position
     */
    public List<BitgetPositionItem> fetchAllPositions(ExchangeApiKey apiKey) {
        List<BitgetPositionItem> allPositions = new ArrayList<>();
        for (String productType : PRODUCT_TYPES) {
            allPositions.addAll(fetchPositionsByProductType(apiKey, productType));
        }
        return allPositions;
    }

    private List<BitgetPositionItem> fetchPositionsByProductType(ExchangeApiKey apiKey, String productType) {
        try {
            String requestPath = "/api/v2/mix/position/all-position";
            String queryString = "productType=" + productType;
            String fullPath = requestPath + "?" + queryString;

            String timestamp = String.valueOf(System.currentTimeMillis());
            HttpHeaders headers = createAuthHeaders(apiKey, timestamp, "GET", fullPath, "");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + fullPath, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return List.of();

            JsonNode root = objectMapper.readTree(body);
            if (!"00000".equals(root.path("code").asText())) {
                log.error("[Bitget] fetchAllPositions({}) 실패 - code: {}, msg: {}",
                        productType, root.path("code").asText(), root.path("msg").asText());
                return List.of();
            }

            JsonNode data = root.path("data");
            if (data.isMissingNode() || !data.isArray()) return List.of();

            return objectMapper.readValue(
                    data.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BitgetPositionItem.class)
            );
        } catch (Exception e) {
            log.error("[Bitget] fetchAllPositions({}) 실패 - apiKeyId: {}", productType, apiKey.getId(), e);
            return List.of();
        }
    }

    private HttpHeaders createAuthHeaders(ExchangeApiKey apiKey, String timestamp,
                                           String method, String requestPath, String body) {
        try {
            String sign = BitgetSignatureUtil.generateSignature(
                    apiKey.getApiSecret(), timestamp, method, requestPath, body != null ? body : "");

            HttpHeaders headers = new HttpHeaders();
            headers.set("ACCESS-KEY", apiKey.getApiKey());
            headers.set("ACCESS-SIGN", sign);
            headers.set("ACCESS-TIMESTAMP", timestamp);
            headers.set("ACCESS-PASSPHRASE", apiKey.getPassphrase());
            headers.set("Content-Type", "application/json");
            headers.set("locale", "en-US");

            if (demoMode) {
                headers.set("paptrading", "1");
            }

            return headers;
        } catch (Exception e) {
            log.error("[Bitget] 서명 생성 실패", e);
            throw new RuntimeException("Bitget signature generation failed", e);
        }
    }
}