package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.PasswordResetToken;
import hello.tradexserver.domain.RefreshToken;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.AuthProvider;
import hello.tradexserver.domain.enums.VerificationType;
import hello.tradexserver.dto.request.*;
import hello.tradexserver.dto.response.AuthResponse;
import hello.tradexserver.dto.response.FindEmailResponse;
import hello.tradexserver.dto.response.UserResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.openApi.rest.ExchangeFactory;
import hello.tradexserver.openApi.rest.ExchangeRestClient;
import hello.tradexserver.openApi.webSocket.ExchangeWebSocketManager;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import hello.tradexserver.repository.PasswordResetTokenRepository;
import hello.tradexserver.repository.RefreshTokenRepository;
import hello.tradexserver.repository.UserRepository;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final ExchangeWebSocketManager exchangeWebSocketManager;
    private final ExchangeFactory exchangeFactory;
    private final VerificationService verificationService;
    private final EmailService emailService;

    private static final int PASSWORD_RESET_EXPIRATION_HOURS = 1;

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AuthException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .username(request.getUsername())
                .phoneNumber(request.getPhoneNumber())
                .socialProvider(AuthProvider.LOCAL)
                .build();

        userRepository.save(user);

        return generateTokens(user);
    }

    public void sendVerificationSms(SendSmsRequest request) {
        VerificationType type = parseVerificationType(request.getType());

        // 회원가입 시에는 이미 등록된 전화번호인지 확인
        if (type == VerificationType.SIGNUP && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AuthException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        // 보안: 아이디 찾기 / 비밀번호 변경 시 미가입 번호면 조용히 리턴 (SMS 미발송, 200 반환)
        if ((type == VerificationType.FIND_EMAIL || type == VerificationType.RESET_PASSWORD)
                && !userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            log.info("SMS skipped for phone: {} (not registered)", request.getPhoneNumber());
            return;
        }

        // 비밀번호 변경 시 소셜 로그인 유저면 조용히 리턴 (비밀번호 없음)
        if (type == VerificationType.RESET_PASSWORD) {
            User user = userRepository.findByPhoneNumber(request.getPhoneNumber()).orElse(null);
            if (user != null && user.getSocialProvider() != AuthProvider.LOCAL) {
                log.info("SMS skipped for phone: {} (social login user)", request.getPhoneNumber());
                return;
            }
        }

        verificationService.sendVerificationCode(request.getPhoneNumber(), type);
    }

    public void verifySms(VerifySmsRequest request) {
        VerificationType type = parseVerificationType(request.getType());
        verificationService.verifyCode(request.getPhoneNumber(), request.getCode(), type);
    }

    public FindEmailResponse findEmail(FindEmailRequest request) {
        verificationService.checkVerified(request.getPhoneNumber(), VerificationType.FIND_EMAIL);

        // 보안: 전화번호 존재 여부를 노출하지 않기 위해 항상 200 반환
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber()).orElse(null);

        if (user == null) {
            log.info("Find email skipped for phone: {} (not registered)", request.getPhoneNumber());
            return FindEmailResponse.builder().maskedEmail(null).build();
        }

        String maskedEmail = maskEmail(user.getEmail());
        return FindEmailResponse.builder().maskedEmail(maskedEmail).build();
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        // 보안: 이메일 존재 여부를 노출하지 않기 위해 항상 200 반환
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        // 이메일이 없거나 소셜 로그인 유저면 조용히 리턴 (200 반환, 이메일 발송 안 함)
        if (user == null || user.getSocialProvider() != AuthProvider.LOCAL) {
            log.info("Password reset skipped for email: {} (not found or social login)", request.getEmail());
            return;
        }

        // 기존 토큰 삭제
        passwordResetTokenRepository.deleteByUser(user);

        // 새 토큰 생성
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(PASSWORD_RESET_EXPIRATION_HOURS))
                .build();

        passwordResetTokenRepository.save(resetToken);

        // 이메일 발송
        emailService.sendPasswordResetEmail(user.getEmail(), token);

        log.info("Password reset email sent to: {}", user.getEmail());
    }

    public void resetPasswordByPhone(Long userId, ResetPasswordByPhoneRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        // 요청한 전화번호가 본인 것인지 확인
        if (!request.getPhoneNumber().equals(user.getPhoneNumber())) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        // SMS 인증 완료 여부 확인
        verificationService.checkVerified(request.getPhoneNumber(), VerificationType.RESET_PASSWORD);

        // 기존 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed by phone for userId: {}", user.getId());
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(request.getToken(), LocalDateTime.now())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_RESET_TOKEN));

        User user = resetToken.getUser();
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.markAsUsed();
        passwordResetTokenRepository.save(resetToken);

        // 기존 리프레시 토큰 삭제 (보안을 위해 재로그인 요구)
        refreshTokenRepository.deleteByUser(user);
    }

    private VerificationType parseVerificationType(String type) {
        try {
            return VerificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AuthException(ErrorCode.INVALID_VERIFICATION_TYPE);
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        int visibleChars = Math.min(2, localPart.length());
        String masked = localPart.substring(0, visibleChars) + "***";
        return masked + domain;
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        return generateTokens(user);
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new AuthException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = refreshToken.getUser();
        return generateTokens(user);
    }

    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.deleteByUser(user);
    }

    public UserResponse completeProfile(Long userId, CompleteProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        // 사용자명 업데이트 (선택)
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.updateProfile(request.getUsername(), null);
        }

        // API Key 검증
        ExchangeApiKey apiKey = ExchangeApiKey.builder()
                .user(user)
                .exchangeName(request.getExchangeName())
                .apiKey(request.getApiKey())
                .apiSecret(request.getApiSecret())
                .passphrase(request.getPassphrase())
                .build();

        ExchangeRestClient client = exchangeFactory.getExchangeService(request.getExchangeName());
        if (!client.validateApiKey(apiKey)) {
            throw new BusinessException(ErrorCode.INVALID_API_KEY);
        }

        // 거래소 API 키 저장
        exchangeApiKeyRepository.save(apiKey);

        // 프로필 완료 처리
        user.completeProfile();
        userRepository.save(user);

        exchangeWebSocketManager.connectUser(userId, apiKey);

        return UserResponse.from(user);
    }

    private AuthResponse generateTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        saveOrUpdateRefreshToken(user, refreshToken);

        return AuthResponse.of(accessToken, refreshToken, user);
    }

    private void saveOrUpdateRefreshToken(User user, String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByUser(user)
                .map(existing -> {
                    existing.updateToken(token, jwtTokenProvider.getRefreshTokenExpiryDate());
                    return existing;
                })
                .orElse(RefreshToken.builder()
                        .user(user)
                        .token(token)
                        .expiryDate(jwtTokenProvider.getRefreshTokenExpiryDate())
                        .build());

        refreshTokenRepository.save(refreshToken);
    }
}