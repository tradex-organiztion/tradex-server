package hello.tradexserver.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private boolean positionEntryEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean positionExitEnabled = true;

    public static NotificationSetting defaultFor(User user) {
        return NotificationSetting.builder()
                .user(user)
                .positionEntryEnabled(true)
                .positionExitEnabled(true)
                .build();
    }

    public void update(boolean positionEntryEnabled, boolean positionExitEnabled) {
        this.positionEntryEnabled = positionEntryEnabled;
        this.positionExitEnabled = positionExitEnabled;
    }
}
