package hello.tradexserver.openApi.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BybitSignatureUtil {

    /**
     * Bybit V5 WebSocket 인증용 서명 생성
     */
    public static String generateSignature(String apiSecret, String timestamp) throws Exception {
        String payload = timestamp;

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
