package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.AuthProvider;
import hello.tradexserver.domain.enums.MemberRole;
import hello.tradexserver.domain.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String username;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider; // LOCAL, GOOGLE, KAKAO 등

    @Column(unique = true)
    private String providerId; // 소셜 로그인 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Builder
    public Member(String email, String password, String username,
                  AuthProvider authProvider, String providerId) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.authProvider = authProvider;
        this.providerId = providerId;
        this.role = MemberRole.ROLE_USER;
        this.status = MemberStatus.ACTIVE;
    }

    public void updateProfile(String username, String profileImageUrl) {
        this.username = username;
        this.profileImageUrl = profileImageUrl;
    }
}