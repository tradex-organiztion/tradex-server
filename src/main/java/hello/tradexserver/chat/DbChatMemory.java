package hello.tradexserver.chat;

import hello.tradexserver.domain.ChatMessage;
import hello.tradexserver.domain.ChatSession;
import hello.tradexserver.repository.ChatMessageRepository;
import hello.tradexserver.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DbChatMemory implements ChatMemory {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

    /**
     * 메시지 저장. [UserMessage, AssistantMessage] 쌍으로 호출됨.
     * conversationId = sessionId
     */
    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        Long sessionId = Long.parseLong(conversationId);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        String question = null;
        String response = null;

        for (Message message : messages) {
            if (message.getMessageType() == MessageType.USER) {
                question = message.getContent();
            } else if (message.getMessageType() == MessageType.ASSISTANT) {
                response = message.getContent();
            }
        }

        if (question != null && response != null) {
            ChatMessage chatMessage = ChatMessage.builder()
                    .user(session.getUser())
                    .chatSession(session)
                    .question(question)
                    .response(response)
                    .build();
            chatMessageRepository.save(chatMessage);
        }
    }

    /**
     * 최근 N개 메시지 반환 (시간 순).
     * 각 ChatMessage → [UserMessage, AssistantMessage] 쌍으로 변환.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId, int lastN) {
        Long sessionId = Long.parseLong(conversationId);
        List<ChatMessage> history = chatMessageRepository.findRecentBySessionId(sessionId, lastN);

        // DESC로 조회됐으므로 역순으로 정렬 (오래된 것 먼저)
        List<Message> messages = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            messages.add(new UserMessage(msg.getQuestion()));
            messages.add(new AssistantMessage(msg.getResponse()));
        }
        return messages;
    }

    /**
     * 세션의 모든 메시지 삭제.
     */
    @Override
    @Transactional
    public void clear(String conversationId) {
        Long sessionId = Long.parseLong(conversationId);
        chatMessageRepository.deleteAllByChatSessionId(sessionId);
    }
}