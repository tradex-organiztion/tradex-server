package hello.tradexserver.openApi.rest;

import hello.tradexserver.domain.Order;
import hello.tradexserver.openApi.rest.dto.BybitClosedPnlResponse;
import hello.tradexserver.openApi.rest.dto.CoinBalanceDto;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public BigDecimal getAsset() {
        WalletBalanceResponse walletBalance = getWalletBalance();
        if (walletBalance != null && walletBalance.getTotalEquity() != null) {
            return walletBalance.getTotalEquity();
        }
        return BigDecimal.ZERO;
    }

    @Override
    @SuppressWarnings("unchecked")
    public WalletBalanceResponse getWalletBalance() {
        String recvWindow = "5000";
        String queryString = "accountType=UNIFIED";

        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = generateSignature(timestamp, recvWindow, queryString);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-BAPI-API-KEY", apiKey);
        headers.set("X-BAPI-TIMESTAMP", timestamp);
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-RECV-WINDOW", recvWindow);

        try {
            String url = BASE_URL + "/account/wallet-balance?" + queryString;
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> response = responseEntity.getBody();

            if (response == null) {
                log.warn("Bybit wallet-balance 응답 없음");
                return WalletBalanceResponse.builder()
                        .totalEquity(BigDecimal.ZERO)
                        .coins(List.of())
                        .build();
            }

            int retCode = (int) response.getOrDefault("retCode", -1);
            if (retCode != 0) {
                log.warn("Bybit wallet-balance 에러: {}", response.get("retMsg"));
                return WalletBalanceResponse.builder()
                        .totalEquity(BigDecimal.ZERO)
                        .coins(List.of())
                        .build();
            }

            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result == null) {
                return WalletBalanceResponse.builder()
                        .totalEquity(BigDecimal.ZERO)
                        .coins(List.of())
                        .build();
            }

            List<Map<String, Object>> accountList = (List<Map<String, Object>>) result.get("list");
            if (accountList == null || accountList.isEmpty()) {
                return WalletBalanceResponse.builder()
                        .totalEquity(BigDecimal.ZERO)
                        .coins(List.of())
                        .build();
            }

            Map<String, Object> account = accountList.get(0);
            String totalEquityStr = (String) account.get("totalEquity");
            BigDecimal totalEquity = totalEquityStr != null ? new BigDecimal(totalEquityStr) : BigDecimal.ZERO;

            List<CoinBalanceDto> coinBalances = new ArrayList<>();
            List<Map<String, Object>> coinList = (List<Map<String, Object>>) account.get("coin");

            if (coinList != null) {
                for (Map<String, Object> coinData : coinList) {
                    String coin = (String) coinData.get("coin");
                    String walletBalanceStr = (String) coinData.get("walletBalance");
                    String usdValueStr = (String) coinData.get("usdValue");

                    BigDecimal walletBalance = walletBalanceStr != null ? new BigDecimal(walletBalanceStr) : BigDecimal.ZERO;
                    BigDecimal usdValue = usdValueStr != null ? new BigDecimal(usdValueStr) : BigDecimal.ZERO;

                    // 잔고가 0보다 큰 코인만 포함
                    if (walletBalance.compareTo(BigDecimal.ZERO) > 0) {
                        coinBalances.add(CoinBalanceDto.builder()
                                .coin(coin)
                                .walletBalance(walletBalance)
                                .usdValue(usdValue)
                                .build());
                    }
                }
            }

            log.info("Bybit 지갑 잔고 조회 성공 - 총 자산: {}, 코인 수: {}", totalEquity, coinBalances.size());

            return WalletBalanceResponse.builder()
                    .totalEquity(totalEquity)
                    .coins(coinBalances)
                    .build();

        } catch (Exception e) {
            log.error("Bybit getWalletBalance 실패", e);
            return WalletBalanceResponse.builder()
                    .totalEquity(BigDecimal.ZERO)
                    .coins(List.of())
                    .build();
        }
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
