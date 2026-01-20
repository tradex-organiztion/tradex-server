package hello.tradexserver.dto.response;

import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long userId;
    private String email;
    private String username;
    private String profileImageUrl;
    private AuthProvider socialProvider;
    private boolean profileCompleted;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .profileImageUrl(user.getProfileImageUrl())
                .socialProvider(user.getSocialProvider())
                .profileCompleted(user.isProfileCompleted())
                .build();
    }
}