package hello.tradexserver.dto.response;

import hello.tradexserver.domain.ChatMessage;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ChatHistoryResponse {

    private final Long sessionId;
    private final String title;
    private final List<MessageEntry> messages;

    public ChatHistoryResponse(Long sessionId, String title, List<ChatMessage> messages) {
        this.sessionId = sessionId;
        this.title = title;
        this.messages = messages.stream()
                .map(MessageEntry::new)
                .toList();
    }

    @Getter
    public static class MessageEntry {
        private final String question;
        private final String response;
        private final LocalDateTime createdAt;

        public MessageEntry(ChatMessage message) {
            this.question = message.getQuestion();
            this.response = message.getResponse();
            this.createdAt = message.getCreatedAt();
        }
    }
}