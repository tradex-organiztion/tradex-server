package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.ChatRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession chatSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}