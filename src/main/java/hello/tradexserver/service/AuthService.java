package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.RefreshToken;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.AuthProvider;
import hello.tradexserver.dto.request.CompleteProfileRequest;
import hello.tradexserver.dto.request.LoginRequest;
import hello.tradexserver.dto.request.SignupRequest;
import hello.tradexserver.dto.request.TokenRefreshRequest;
import hello.tradexserver.dto.response.AuthResponse;
import hello.tradexserver.dto.response.UserResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import hello.tradexserver.repository.RefreshTokenRepository;
import hello.tradexserver.repository.UserRepository;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .username(request.getUsername())
                .socialProvider(AuthProvider.LOCAL)
                .build();

        userRepository.save(user);

        return generateTokens(user);
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

        // 거래소 API 키 저장
        ExchangeApiKey apiKey = ExchangeApiKey.builder()
                .user(user)
                .exchangeName(request.getExchangeName())
                .apiKey(request.getApiKey())
                .apiSecret(request.getApiSecret())
                .build();
        exchangeApiKeyRepository.save(apiKey);

        // 프로필 완료 처리
        user.completeProfile();
        userRepository.save(user);

        return UserResponse.from(user);
    }

    private AuthResponse generateTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getEmail()
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