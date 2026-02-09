package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.openApi.rest.dto.*;
import hello.tradexserver.dto.response.PositionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ByBitRestClient implements ExchangeRestClient {

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

        HttpHeaders headers = createHeaders(timestamp, recvWindow, signature);

        try {
            String url = BASE_URL + "/position/closed-pnl?" + queryString;
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BybitClosedPnlResponse> responseEntity = restTemplate.exchange(
                    url, HttpMethod.GET, entity, BybitClosedPnlResponse.class
            );

            BybitClosedPnlResponse response = responseEntity.getBody();

            if (response == null || response.getResult() == null || response.getResult().getList() == null) {
                log.info("[Bybit] closed-pnl 값 없음");
                return List.of();
            }

            log.info("[Bybit] closed-pnl 응답 코드: {}", response.getRetCode());
            return List.of();
        } catch (Exception e) {
            throw new RuntimeException("Bybit getClosedPositions 실패", e);
        }
    }

    @Override
    public List<TradeRecord> getTradeHistory(String symbol, Long startTime, int limit) {
        String recvWindow = "5000";
        StringBuilder qs = new StringBuilder("category=linear&limit=" + limit);
        if (symbol != null && !symbol.isEmpty()) {
            qs.append("&symbol=").append(symbol);
        }
        if (startTime != null) {
            qs.append("&startTime=").append(startTime);
        }
        String queryString = qs.toString();

        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = generateSignature(timestamp, recvWindow, queryString);
        HttpHeaders headers = createHeaders(timestamp, recvWindow, signature);

        try {
            String url = BASE_URL + "/execution/list?" + queryString;
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BybitExecutionResponse> responseEntity = restTemplate.exchange(
                    url, HttpMethod.GET, entity, BybitExecutionResponse.class
            );

            BybitExecutionResponse response = responseEntity.getBody();
            if (response == null || response.getResult() == null || response.getResult().getList() == null) {
                log.info("[Bybit] execution/list 값 없음");
                return List.of();
            }

            log.info("[Bybit] execution/list 응답 코드: {}, 건수: {}",
                    response.getRetCode(), response.getResult().getList().size());

            List<TradeRecord> trades = new ArrayList<>();
            for (BybitExecution exec : response.getResult().getList()) {
                trades.add(TradeRecord.builder()
                        .tradeId(exec.getExecId())
                        .orderId(exec.getOrderId())
                        .symbol(exec.getSymbol())
                        .side("Buy".equalsIgnoreCase(exec.getSide()) ? OrderSide.BUY : OrderSide.SELL)
                        .price(parseBigDecimal(exec.getExecPrice()))
                        .quantity(parseBigDecimal(exec.getExecQty()))
                        .fee(parseBigDecimal(exec.getExecFee()))
                        .feeCurrency(exec.getFeeCurrency())
                        .realizedPnl(parseBigDecimal(exec.getClosedPnl()))
                        .tradeTime(parseTimestamp(exec.getExecTime()))
                        .build());
            }
            return trades;
        } catch (Exception e) {
            throw new RuntimeException("Bybit getTradeHistory 실패", e);
        }
    }

    public List<Order> getOrders(int limit) {
        String recvWindow = "5000";
        String queryString = "category=linear&limit=" + limit;

        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = generateSignature(timestamp, recvWindow, queryString);
        HttpHeaders headers = createHeaders(timestamp, recvWindow, signature);

        try {
            String url = BASE_URL + "/order/history?" + queryString;
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BybitOrderHistoryResponse> responseEntity = restTemplate.exchange(
                    url, HttpMethod.GET, entity, BybitOrderHistoryResponse.class
            );

            BybitOrderHistoryResponse response = responseEntity.getBody();
            if (response == null || response.getResult() == null || response.getResult().getList() == null) {
                log.info("[Bybit] order/history 값 없음");
                return List.of();
            }

            log.info("[Bybit] order/history 응답 코드: {}", response.getRetCode());
            return List.of();
        } catch (Exception e) {
            throw new RuntimeException("Bybit getOrdersHistory 실패", e);
        }
    }

    @Override
    public BigDecimal getAsset() {
        log.info("[Bybit] getAsset - TODO");
        return null;
    }

    private HttpHeaders createHeaders(String timestamp, String recvWindow, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-BAPI-API-KEY", apiKey);
        headers.set("X-BAPI-TIMESTAMP", timestamp);
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-RECV-WINDOW", recvWindow);
        return headers;
    }

    private String generateSignature(String timestamp, String recvWindow, String queryString) {
        String payload = timestamp + apiKey + recvWindow + queryString;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(payload.getBytes());

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

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            long millis = Long.parseLong(timestamp);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
