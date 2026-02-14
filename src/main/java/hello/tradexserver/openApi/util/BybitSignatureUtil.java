package hello.tradexserver.openApi.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BybitSignatureUtil {

    /**
     * Bybit V5 REST API 서명 생성
     * payload = timestamp + apiKey + recvWindow + queryString
     */
    public static String generateRestSignature(String apiSecret, String apiKey,
                                               String timestamp, String recvWindow,
                                               String queryString) {
        String payload = timestamp + apiKey + recvWindow + queryString;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Bybit REST 서명 생성 실패", e);
        }
    }

    /**
     * Bybit V5 WebSocket 인증용 서명 생성
     * payload = "GET/realtime" + expires
     */
    public static String generateSignature(String apiSecret, String expires) throws Exception {
        String payload = "GET/realtime" + expires;

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                apiSecret.getBytes(),
                0,
                apiSecret.getBytes().length,
                "HmacSHA256"
        );
        mac.init(secretKey);

        byte[] signedMessage = mac.doFinal(payload.getBytes());

        // Hex 인코딩
        StringBuilder hexString = new StringBuilder();
        for (byte b : signedMessage) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
