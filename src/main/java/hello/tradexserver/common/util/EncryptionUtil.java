package hello.tradexserver.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static String secretKey;

    @Value("${encryption.secret-key}")
    public void setSecretKey(String key) {
        EncryptionUtil.secretKey = key;
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // 랜덤 IV 생성
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

            // IV + 암호문을 합쳐서 저장
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // IV와 암호문 분리
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}