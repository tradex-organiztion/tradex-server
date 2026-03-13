package hello.tradexserver.openApi.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.dto.BinanceAllOrderItem;
import hello.tradexserver.openApi.rest.dto.BinancePositionRisk;
import hello.tradexserver.openApi.rest.dto.BinanceUserTrade;
import hello.tradexserver.openApi.rest.dto.CoinBalanceDto;
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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceRestClient implements ExchangeRestClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

//     private static final String BASE_URL = "https://testnet.binancefuture.com";
    private static final String BASE_URL = "https://fapi.binance.com";

    @Override
    public boolean validateApiKey(ExchangeApiKey apiKey) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String queryString = "timestamp=" + timestamp;
            String signature = BinanceSignatureUtil.generateSignature(apiKey.getApiSecret(), queryString);
            queryString += "&signature=" + signature;

            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/fapi/v3/balance?" + queryString,
                    HttpMethod.GET, entity, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("[Binance] API Key 검증 실패 - apiKeyId: {}", apiKey.getId(), e);
            return false;
        }
    }

    /**
     * User Data Stream listenKey 생성
     */
    public String createListenKey(ExchangeApiKey apiKey) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/fapi/v1/listenKey", HttpMethod.POST, entity, String.class);

            String body = response.getBody();
            if (body != null) {
                String listenKey = objectMapper.readTree(body).path("listenKey").asText(null);
                if (listenKey != null && !listenKey.isEmpty()) {
                    log.info("[Binance] ListenKey 생성 완료");
                    return listenKey;
                }
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
    public void keepAliveListenKey(ExchangeApiKey apiKey, String listenKey) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            restTemplate.exchange(
                    BASE_URL + "/fapi/v1/listenKey?listenKey=" + listenKey,
                    HttpMethod.PUT, entity, String.class);
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
        WalletBalanceResponse walletBalance = getWalletBalance(apiKey);
        if (walletBalance != null && walletBalance.getTotalEquity() != null) {
            return walletBalance.getTotalEquity();
        }
        return BigDecimal.ZERO;
    }

    @Override
    public WalletBalanceResponse getWalletBalance(ExchangeApiKey apiKey) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String queryString = "timestamp=" + timestamp;
            String signature = BinanceSignatureUtil.generateSignature(apiKey.getApiSecret(), queryString);
            queryString += "&signature=" + signature;

            HttpEntity<String> entity = new HttpEntity<>(createApiKeyHeader(apiKey));
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/fapi/v3/account?" + queryString,
                    HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            log.info("[Binance] account 원본 응답: {}", body);
            if (body == null) {
                log.warn("[Binance] account 응답 없음");
                return WalletBalanceResponse.builder().totalEquity(BigDecimal.ZERO).coins(List.of()).build();
            }

            JsonNode root = objectMapper.readTree(body);

            BigDecimal totalEquity = BigDecimal.ZERO;
            List<CoinBalanceDto> coinBalances = new ArrayList<>();
            JsonNode assets = root.path("assets");
            if (assets.isArray()) {
                for (JsonNode asset : assets) {
                    BigDecimal marginBal = parseBigDecimal(asset.path("marginBalance").asText("0"));
                    totalEquity = totalEquity.add(marginBal);

                    BigDecimal walletBal = parseBigDecimal(asset.path("walletBalance").asText("0"));
                    if (walletBal.compareTo(BigDecimal.ZERO) == 0) continue;

                    coinBalances.add(CoinBalanceDto.builder()
                            .coin(asset.path("asset").asText())
                            .walletBalance(walletBal)
                            .usdValue(marginBal)
                            .build());
                }
            }

            log.info("[Binance] 지갑 잔고 조회 성공 - 총 자산: {}, 코인 수: {}", totalEquity, coinBalances.size());
            return WalletBalanceResponse.builder().totalEquity(totalEquity).coins(coinBalances).build();
        } catch (Exception e) {
            log.error("[Binance] 지갑 잔고 조회 실패 - apiKeyId: {}", apiKey.getId(), e);
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

    private HttpHeaders createApiKeyHeader(ExchangeApiKey apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey.getApiKey());
        return headers;
    }
}