package hello.tradexserver.domain;

import hello.tradexserver.common.converter.EncryptedStringConverter;
import hello.tradexserver.domain.enums.ExchangeName;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_api_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "exchange_name"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExchangeName exchangeName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 500)
    private String apiKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 500)
    private String apiSecret;

    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}