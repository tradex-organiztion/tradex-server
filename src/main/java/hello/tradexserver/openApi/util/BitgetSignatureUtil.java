package hello.tradexserver.openApi.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BitgetSignatureUtil {

    /**
     * Bitget V2 REST API 서명 생성
     * sign = Base64(HMAC-SHA256(secretKey, timestamp + method + requestPath + body))
     * 주의: Binance/Bybit는 Hex 인코딩이지만, Bitget은 Base64 인코딩
     */
    public static String generateSignature(String apiSecret, String timestamp,
                                            String method, String requestPath,
                                            String body) throws Exception {
        String payload = timestamp + method.toUpperCase() + requestPath + (body != null ? body : "");

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(
                apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(key);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Bitget V2 WebSocket 로그인 서명 생성
     * sign = Base64(HMAC-SHA256(secretKey, timestamp + "GET" + "/user/verify"))
     * 주의: timestamp는 초 단위 (밀리초 아님)
     */
    public static String generateWebSocketSignature(String apiSecret, String timestamp)
            throws Exception {
        String payload = timestamp + "GET" + "/user/verify";

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(
                apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(key);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hash);
    }
}