package hello.tradexserver.openApi.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.domain.enums.PositionEffect;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.openApi.rest.dto.BitgetBaseResponse;
import hello.tradexserver.openApi.rest.dto.BitgetFillItem;
import hello.tradexserver.openApi.rest.dto.TradeRecord;
import hello.tradexserver.openApi.util.BitgetSignatureUtil;
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
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class BitgetRestClient implements ExchangeRestClient {

    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://api.bitget.com";

    @Override
    public List<PositionResponse> getPositions(int limit) {
        try {
            String requestPath = "/api/v2/mix/position/all-position";
            String queryString = "productType=USDT-FUTURES";
            String fullPath = requestPath + "?" + queryString;

            String timestamp = String.valueOf(System.currentTimeMillis());
            HttpHeaders headers = createAuthHeaders(timestamp, "GET", fullPath, null);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + fullPath;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class
            );

            log.info("[Bitget] all-position 응답");
            log.debug("[Bitget] all-position raw: {}", response.getBody());
            return List.of();
        } catch (Exception e) {
            log.error("[Bitget] getPositions 실패", e);
            throw new RuntimeException("Bitget getPositions 실패", e);
        }
    }

    @Override
    public List<TradeRecord> getTradeHistory(String symbol, Long startTime, int limit) {
        try {
            String requestPath = "/api/v2/mix/order/fill-history";
            StringBuilder qs = new StringBuilder("productType=USDT-FUTURES&limit=" + limit);
            if (symbol != null && !symbol.isEmpty()) {
                qs.append("&symbol=").append(symbol);
            }
            if (startTime != null) {
                qs.append("&startTime=").append(startTime);
            }
            String fullPath = requestPath + "?" + qs;

            String timestamp = String.valueOf(System.currentTimeMillis());
            HttpHeaders headers = createAuthHeaders(timestamp, "GET", fullPath, null);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + fullPath;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class
            );

            String body = response.getBody();
            if (body == null) {
                return List.of();
            }

            // Bitget 응답: { "code": "00000", "msg": "success", "data": { "fillList": [...] } }
            Map<String, Object> responseMap = objectMapper.readValue(body,
                    new TypeReference<Map<String, Object>>() {});

            String code = (String) responseMap.get("code");
            if (!"00000".equals(code)) {
                log.warn("[Bitget] fill-history 응답 에러: code={}, msg={}", code, responseMap.get("msg"));
                return List.of();
            }

            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            if (data == null) return List.of();

            Object fillListObj = data.get("fillList");
            if (fillListObj == null) return List.of();

            List<BitgetFillItem> fills = objectMapper.convertValue(fillListObj,
                    new TypeReference<List<BitgetFillItem>>() {});

            List<TradeRecord> trades = new ArrayList<>();
            for (BitgetFillItem fill : fills) {
                PositionEffect effect = null;
                if ("open".equalsIgnoreCase(fill.getTradeSide())) {
                    effect = PositionEffect.OPEN;
                } else if ("close".equalsIgnoreCase(fill.getTradeSide())) {
                    effect = PositionEffect.CLOSE;
                }

                trades.add(TradeRecord.builder()
                        .tradeId(fill.getTradeId())
                        .orderId(fill.getOrderId())
                        .symbol(fill.getSymbol())
                        .side("buy".equalsIgnoreCase(fill.getSide()) ? OrderSide.BUY : OrderSide.SELL)
                        .positionEffect(effect)
                        .price(parseBigDecimal(fill.getPrice()))
                        .quantity(parseBigDecimal(fill.getSize()))
                        .fee(parseBigDecimal(fill.getFee()))
                        .feeCurrency(fill.getFeeCurrency())
                        .realizedPnl(parseBigDecimal(fill.getProfit()))
                        .tradeTime(parseTimestamp(fill.getCTime()))
                        .build());
            }

            log.info("[Bitget] fill-history 건수: {}", trades.size());
            return trades;
        } catch (Exception e) {
            log.error("[Bitget] getTradeHistory 실패", e);
            throw new RuntimeException("Bitget getTradeHistory 실패", e);
        }
    }

    @Override
    public BigDecimal getAsset() {
        log.info("[Bitget] getAsset - TODO");
        return null;
    }

    private HttpHeaders createAuthHeaders(String timestamp, String method,
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
        return headers;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
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