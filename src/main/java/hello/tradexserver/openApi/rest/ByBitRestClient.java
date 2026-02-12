package hello.tradexserver.openApi.rest.bybit;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.ExchangeRestClient;
import hello.tradexserver.openApi.rest.dto.*;
import hello.tradexserver.openApi.util.BybitSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BybitRestClient implements ExchangeRestClient {

    private final RestTemplate restTemplate;

    private static final String BASE_URL = "https://api-demo.bybit.com/v5";
    // private static final String BASE_URL = "https://api.bybit.com/v5";

    public BybitOrderHistoryData fetchOrderHistory(ExchangeApiKey apiKey, String symbol,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder qs = new StringBuilder("category=linear&limit=50");
        if (symbol != null && !symbol.isEmpty()) qs.append("&symbol=").append(symbol);
        if (startTime != null) qs.append("&startTime=").append(toEpochMilli(startTime));
        if (endTime != null) qs.append("&endTime=").append(toEpochMilli(endTime));
        String queryString = qs.toString();

        try {
            HttpEntity<String> entity = new HttpEntity<>(createSignedHeaders(apiKey, queryString));
            ResponseEntity<BybitOrderHistoryResponse> response = restTemplate.exchange(
                    BASE_URL + "/order/history?" + queryString,
                    HttpMethod.GET, entity, BybitOrderHistoryResponse.class
            );
            BybitOrderHistoryResponse body = response.getBody();
            if (body == null || body.getResult() == null) {
                log.info("[Bybit] order/history 응답 없음 - apiKeyId: {}", apiKey.getId());
                return null;
            }
            if (body.getRetCode() != 0) {
                log.warn("[Bybit] order/history 오류 - retCode: {}, msg: {}", body.getRetCode(), body.getRetMsg());
                return null;
            }
            return body.getResult();
        } catch (Exception e) {
            log.error("[Bybit] order/history 조회 실패 - apiKeyId: {}", apiKey.getId(), e);
            return null;
        }
    }

    public List<BybitPositionRestItem> fetchOpenPositions(ExchangeApiKey apiKey) {
        String queryString = "category=linear&settleCoin=USDT&limit=200";

        try {
            HttpEntity<String> entity = new HttpEntity<>(createSignedHeaders(apiKey, queryString));
            ResponseEntity<BybitPositionListResponse> response = restTemplate.exchange(
                    BASE_URL + "/position/list?" + queryString,
                    HttpMethod.GET, entity, BybitPositionListResponse.class
            );
            BybitPositionListResponse body = response.getBody();
            if (body == null || body.getResult() == null) {
                log.info("[Bybit] position/list 응답 없음 - apiKeyId: {}", apiKey.getId());
                return List.of();
            }
            if (body.getRetCode() != 0) {
                log.warn("[Bybit] position/list 오류 - retCode: {}, msg: {}", body.getRetCode(), body.getRetMsg());
                return List.of();
            }
            List<BybitPositionRestItem> list = body.getResult().getList();
            if (list == null) return List.of();

            return list.stream()
                    .filter(item -> item.getSide() != null && !item.getSide().isEmpty())
                    .toList();
        } catch (Exception e) {
            log.error("[Bybit] position/list 조회 실패 - apiKeyId: {}", apiKey.getId(), e);
            return List.of();
        }
    }

    @Override
    public BigDecimal getAsset(ExchangeApiKey apiKey) {
        // TODO: implement
        log.info("[Bybit] getAsset - TODO");
        return null;
    }

    public BybitClosedPnlData fetchClosedPnl(ExchangeApiKey apiKey, String symbol,
                                              LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder qs = new StringBuilder("category=linear&limit=100");
        if (symbol != null && !symbol.isEmpty()) qs.append("&symbol=").append(symbol);
        if (startTime != null) qs.append("&startTime=").append(toEpochMilli(startTime));
        if (endTime != null) qs.append("&endTime=").append(toEpochMilli(endTime));
        String queryString = qs.toString();

        try {
            HttpEntity<String> entity = new HttpEntity<>(createSignedHeaders(apiKey, queryString));
            ResponseEntity<BybitClosedPnlResponse> response = restTemplate.exchange(
                    BASE_URL + "/position/closed-pnl?" + queryString,
                    HttpMethod.GET, entity, BybitClosedPnlResponse.class
            );
            BybitClosedPnlResponse body = response.getBody();
            if (body == null || body.getResult() == null) {
                log.info("[Bybit] closed-pnl 응답 없음 - apiKeyId: {}", apiKey.getId());
                return null;
            }
            if (body.getRetCode() != 0) {
                log.warn("[Bybit] closed-pnl 오류 - retCode: {}, msg: {}", body.getRetCode(), body.getRetMsg());
                return null;
            }
            return body.getResult();
        } catch (Exception e) {
            log.error("[Bybit] closed-pnl 조회 실패 - apiKeyId: {}", apiKey.getId(), e);
            return null;
        }
    }

    private HttpHeaders createSignedHeaders(ExchangeApiKey apiKey, String queryString) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String recvWindow = "5000";
        String signature = BybitSignatureUtil.generateRestSignature(
                apiKey.getApiSecret(), apiKey.getApiKey(), timestamp, recvWindow, queryString);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-BAPI-API-KEY", apiKey.getApiKey());
        headers.set("X-BAPI-TIMESTAMP", timestamp);
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-RECV-WINDOW", recvWindow);
        return headers;
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}