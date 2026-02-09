package hello.tradexserver.openApi.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BinanceSignatureUtil {

    /**
     * Binance Futures REST API 서명 생성
     * HMAC SHA256 사용
     */
    public static String generateSignature(String apiSecret, String queryString) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                apiSecret.getBytes(),
                "HmacSHA256"
        );
        mac.init(secretKey);

        byte[] signedMessage = mac.doFinal(queryString.getBytes());

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