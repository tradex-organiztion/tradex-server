package hello.tradexserver.openApi.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.openApi.rest.dto.BinanceTrade;
import hello.tradexserver.openApi.rest.dto.TradeRecord;
import hello.tradexserver.openApi.util.BinanceSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class BinanceRestClient implements ExchangeRestClient {

    private final String apiKey;
    private final String apiSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public List<TradeRecord> getTradeHistory(String symbol, Long startTime, int limit) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            StringBuilder qs = new StringBuilder("symbol=" + symbol + "&timestamp=" + timestamp + "&limit=" + limit);
            if (startTime != null) {
                qs.append("&startTime=").append(startTime);
            }
            String queryString = qs.toString();

            String signature = BinanceSignatureUtil.generateSignature(apiSecret, queryString);
            queryString += "&signature=" + signature;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MBX-APIKEY", apiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + "/fapi/v1/userTrades?" + queryString;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class
            );

            String body = response.getBody();
            if (body == null || !body.startsWith("[")) {
                log.info("[Binance] userTrades 값 없음");
                return List.of();
            }

            List<BinanceTrade> binanceTrades = objectMapper.readValue(body,
                    new TypeReference<List<BinanceTrade>>() {});

            List<TradeRecord> trades = new ArrayList<>();
            for (BinanceTrade bt : binanceTrades) {
                trades.add(TradeRecord.builder()
                        .tradeId(String.valueOf(bt.getId()))
                        .orderId(bt.getOrderId())
                        .symbol(bt.getSymbol())
                        .side("BUY".equalsIgnoreCase(bt.getSide()) ? OrderSide.BUY : OrderSide.SELL)
                        .price(parseBigDecimal(bt.getPrice()))
                        .quantity(parseBigDecimal(bt.getQty()))
                        .fee(parseBigDecimal(bt.getCommission()))
                        .feeCurrency(bt.getCommissionAsset())
                        .realizedPnl(parseBigDecimal(bt.getRealizedPnl()))
                        .tradeTime(parseTimestamp(bt.getTime()))
                        .build());
            }

            log.info("[Binance] userTrades 건수: {}", trades.size());
            return trades;
        } catch (Exception e) {
            log.error("[Binance] getTradeHistory 실패", e);
            throw new RuntimeException("Binance getTradeHistory 실패", e);
        }
    }

    @Override
    public List<PositionResponse> getPositions(int limit) {
        getPositionRisk(null);
        return List.of();
    }

    @Override
    public BigDecimal getAsset() {
        return null;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseTimestamp(Long millis) {
        if (millis == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
