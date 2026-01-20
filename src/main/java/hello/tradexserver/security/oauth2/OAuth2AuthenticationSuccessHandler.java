package hello.tradexserver.security.oauth2;

import hello.tradexserver.domain.RefreshToken;
import hello.tradexserver.domain.User;
import hello.tradexserver.repository.RefreshTokenRepository;
import hello.tradexserver.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        saveOrUpdateRefreshToken(user, refreshToken);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("profileCompleted", user.isProfileCompleted())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
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