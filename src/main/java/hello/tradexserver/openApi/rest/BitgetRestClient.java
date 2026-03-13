package hello.tradexserver.openApi.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.dto.BitgetOrderHistoryItem;
import hello.tradexserver.openApi.rest.dto.BitgetPositionItem;
import hello.tradexserver.openApi.rest.dto.CoinBalanceDto;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;
import hello.tradexserver.openApi.util.BitgetSignatureUtil;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class BitgetRestClient implements ExchangeRestClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "https://api.bitget.com";

    // 데모 모드 여부 (데모 트레이딩 시 true)
    private static final boolean IS_DEMO_MODE = true;

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
                    BASE_URL + fullPath, HttpMethod.GET, entity, String.class);

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
        WalletBalanceResponse walletBalance = getWalletBalance(apiKey);
        if (walletBalance != null && walletBalance.getTotalEquity() != null) {
            return walletBalance.getTotalEquity();
        }
        return BigDecimal.ZERO;
    }

    @Override
    public WalletBalanceResponse getWalletBalance(ExchangeApiKey apiKey) {
        try {
            String requestPath = "/api/v2/mix/account/accounts";
            String queryString = "productType=USDT-FUTURES";
            String fullPath = requestPath + "?" + queryString;

            String timestamp = String.valueOf(System.currentTimeMillis());
            HttpHeaders headers = createAuthHeaders(apiKey, timestamp, "GET", fullPath, "");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + fullPath, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            log.info("[Bitget] accounts 원본 응답: {}", body);
            if (body == null) {
                log.warn("[Bitget] accounts 응답 없음");
                return WalletBalanceResponse.builder().totalEquity(BigDecimal.ZERO).coins(List.of()).build();
            }

            JsonNode root = objectMapper.readTree(body);
            if (!"00000".equals(root.path("code").asText())) {
                log.warn("[Bitget] accounts 에러 - code: {}, msg: {}",
                        root.path("code").asText(), root.path("msg").asText());
                return WalletBalanceResponse.builder().totalEquity(BigDecimal.ZERO).coins(List.of()).build();
            }

            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) {
                return WalletBalanceResponse.builder().totalEquity(BigDecimal.ZERO).coins(List.of()).build();
            }

            BigDecimal totalEquity = BigDecimal.ZERO;
            List<CoinBalanceDto> coinBalances = new ArrayList<>();

            for (JsonNode account : dataArray) {
                BigDecimal usdtEquity = parseBigDecimal(account.path("usdtEquity").asText("0"));
                totalEquity = totalEquity.add(usdtEquity);

                BigDecimal available = parseBigDecimal(account.path("available").asText("0"));
                BigDecimal locked = parseBigDecimal(account.path("locked").asText("0"));
                BigDecimal walletBal = available.add(locked);

                if (walletBal.compareTo(BigDecimal.ZERO) == 0) continue;

                coinBalances.add(CoinBalanceDto.builder()
                        .coin(account.path("marginCoin").asText())
                        .walletBalance(walletBal)
                        .usdValue(usdtEquity)
                        .build());
            }

            log.info("[Bitget] 지갑 잔고 조회 성공 - 총 자산: {}, 코인 수: {}", totalEquity, coinBalances.size());
            return WalletBalanceResponse.builder().totalEquity(totalEquity).coins(coinBalances).build();
        } catch (Exception e) {
            log.error("[Bitget] 지갑 잔고 조회 실패 - apiKeyId: {}", apiKey.getId(), e);
            return WalletBalanceResponse.builder().totalEquity(BigDecimal.ZERO).coins(List.of()).build();
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 오더 히스토리 조회
     * GET /api/v2/mix/order/orders-history
     */
    public List<BitgetOrderHistoryItem> fetchOrderHistory(ExchangeApiKey apiKey, String symbol,
                                                           Long startTime, Long endTime) {
        try {
            String requestPath = "/api/v2/mix/order/orders-history";
            StringBuilder qs = new StringBuilder("productType=USDT-FUTURES");
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
                    BASE_URL + fullPath, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return List.of();

            JsonNode root = objectMapper.readTree(body);
            if (!"00000".equals(root.path("code").asText())) {
                log.error("[Bitget] fetchOrderHistory 실패 - code: {}, msg: {}",
                        root.path("code").asText(), root.path("msg").asText());
                return List.of();
            }

            JsonNode orderList = root.path("data").path("entrustedList");
            if (orderList.isMissingNode() || !orderList.isArray()) return List.of();

            return objectMapper.readValue(
                    orderList.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BitgetOrderHistoryItem.class)
            );
        } catch (Exception e) {
            log.error("[Bitget] fetchOrderHistory 실패 - apiKeyId: {}", apiKey.getId(), e);
            return List.of();
        }
    }

    /**
     * 전체 포지션 조회
     * GET /api/v2/mix/position/all-position
     */
    public List<BitgetPositionItem> fetchAllPositions(ExchangeApiKey apiKey) {
        try {
            String requestPath = "/api/v2/mix/position/all-position";
            String queryString = "productType=USDT-FUTURES";
            String fullPath = requestPath + "?" + queryString;

            String timestamp = String.valueOf(System.currentTimeMillis());
            HttpHeaders headers = createAuthHeaders(apiKey, timestamp, "GET", fullPath, "");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + fullPath, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null) return List.of();

            JsonNode root = objectMapper.readTree(body);
            if (!"00000".equals(root.path("code").asText())) {
                log.error("[Bitget] fetchAllPositions 실패 - code: {}, msg: {}",
                        root.path("code").asText(), root.path("msg").asText());
                return List.of();
            }

            JsonNode data = root.path("data");
            if (data.isMissingNode() || !data.isArray()) return List.of();

            return objectMapper.readValue(
                    data.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BitgetPositionItem.class)
            );
        } catch (Exception e) {
            log.error("[Bitget] fetchAllPositions 실패 - apiKeyId: {}", apiKey.getId(), e);
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

            if (IS_DEMO_MODE) {
                headers.set("paptrading", "1");
            }

            return headers;
        } catch (Exception e) {
            log.error("[Bitget] 서명 생성 실패", e);
            throw new RuntimeException("Bitget signature generation failed", e);
        }
    }
}