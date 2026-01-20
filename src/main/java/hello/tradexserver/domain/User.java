package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(columnDefinition = "TEXT")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AuthProvider socialProvider;

    private String socialId;

    @Column(nullable = false)
    @Builder.Default
    private boolean profileCompleted = false;

    public void completeProfile() {
        this.profileCompleted = true;
    }

    public void updateProfile(String username, String profileImageUrl) {
        if (username != null) {
            this.username = username;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}