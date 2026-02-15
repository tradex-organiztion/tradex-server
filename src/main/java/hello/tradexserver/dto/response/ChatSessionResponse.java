package hello.tradexserver.dto.response;

import hello.tradexserver.domain.ChatSession;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ChatSessionResponse {

    private final Long sessionId;
    private final String title;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ChatSessionResponse(ChatSession session) {
        this.sessionId = session.getId();
        this.title = session.getTitle();
        this.createdAt = session.getCreatedAt();
        this.updatedAt = session.getUpdatedAt();
    }
}