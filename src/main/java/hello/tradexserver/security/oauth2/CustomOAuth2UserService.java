package hello.tradexserver.security.oauth2;

import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.AuthProvider;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                registrationId, oAuth2User.getAttributes()
        );

        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        User user = userRepository.findBySocialProviderAndSocialId(provider, userInfo.getId())
                .map(existingUser -> updateExistingUser(existingUser, userInfo))
                .orElseGet(() -> registerNewUser(provider, userInfo));

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private User registerNewUser(AuthProvider provider, OAuth2UserInfo userInfo) {
        User user = User.builder()
                .email(userInfo.getEmail())
                .username(userInfo.getName())
                .profileImageUrl(userInfo.getImageUrl())
                .socialProvider(provider)
                .socialId(userInfo.getId())
                .build();
        return userRepository.save(user);
    }

    private User updateExistingUser(User user, OAuth2UserInfo userInfo) {
        user.updateProfile(userInfo.getName(), userInfo.getImageUrl());
        return userRepository.save(user);
    }
}