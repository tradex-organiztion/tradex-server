package hello.tradexserver.service;

import hello.tradexserver.domain.VerificationCode;
import hello.tradexserver.domain.enums.VerificationType;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VerificationService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final SmsService smsService;

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 5;

    public void sendVerificationCode(String phoneNumber, VerificationType type) {
        // 기존 인증번호 삭제
        verificationCodeRepository.deleteByPhoneNumberAndType(phoneNumber, type);

        // 새 인증번호 생성
        String code = generateCode();

        // DB에 저장
        VerificationCode verificationCode = VerificationCode.builder()
                .phoneNumber(phoneNumber)
                .code(code)
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES))
                .build();

        verificationCodeRepository.save(verificationCode);

        // SMS 발송
        smsService.sendVerificationCode(phoneNumber, code);

        log.info("Verification code sent to: {} for type: {}", phoneNumber, type);
    }

    public boolean verifyCode(String phoneNumber, String code, VerificationType type) {
        VerificationCode verificationCode = verificationCodeRepository
                .findByPhoneNumberAndCodeAndTypeAndVerifiedFalseAndExpiresAtAfter(
                        phoneNumber, code, type, LocalDateTime.now())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_VERIFICATION_CODE));

        verificationCode.verify();
        verificationCodeRepository.save(verificationCode);

        log.info("Verification code verified for: {} type: {}", phoneNumber, type);
        return true;
    }

    public boolean isVerified(String phoneNumber, VerificationType type) {
        return verificationCodeRepository
                .findByPhoneNumberAndTypeAndVerifiedFalseAndExpiresAtAfter(
                        phoneNumber, type, LocalDateTime.now())
                .map(VerificationCode::isExpired)
                .map(expired -> !expired)
                .orElse(false);
    }

    public void checkVerified(String phoneNumber, VerificationType type) {
        verificationCodeRepository
                .findByPhoneNumberAndTypeAndVerifiedFalseAndExpiresAtAfter(
                        phoneNumber, type, LocalDateTime.now())
                .filter(vc -> !vc.isExpired())
                .orElseThrow(() -> new AuthException(ErrorCode.PHONE_NOT_VERIFIED));
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    // 만료된 인증번호 정리 (스케줄러에서 호출)
    public void cleanupExpiredCodes() {
        verificationCodeRepository.deleteExpiredCodes(LocalDateTime.now());
        log.info("Expired verification codes cleaned up");
    }
}
