package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.Theme;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSetting {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Theme theme = Theme.SYSTEM;

    @Column(length = 10)
    @Builder.Default
    private String language = "ko";

    @Builder.Default
    private Boolean pushNotificationEnabled = true;

    @Builder.Default
    private Boolean appNotificationEnabled = true;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}