package hello.tradexserver.security.oauth2;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId,
                                                    Map<String, Object> attributes) {
        return switch (registrationId.toUpperCase()) {
            case "GOOGLE" -> new GoogleOAuth2UserInfo(attributes);
            case "KAKAO" -> new KakaoOAuth2UserInfo(attributes);
            default -> throw new IllegalArgumentException(
                    "Unsupported OAuth2 provider: " + registrationId
            );
        };
    }
}