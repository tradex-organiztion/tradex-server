package hello.tradexserver.service;

import hello.tradexserver.domain.ChatMessage;
import hello.tradexserver.domain.User;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.repository.ChatMessageRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Media;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static hello.tradexserver.exception.ErrorCode.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final StreamingChatModel streamingChatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    private static final int HISTORY_LIMIT = 5;

    public SseEmitter streamChat(Long userId, String question, MultipartFile file) {
        SseEmitter emitter = new SseEmitter(300000L);

        new Thread(() -> {
            try {
                // 사용자 조회
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new AuthException(USER_NOT_FOUND));

                // 채팅 히스토리 조회
                List<ChatMessage> history = chatMessageRepository
                        .findRecentHistory(userId, HISTORY_LIMIT);

                // 프롬프트 구성
                Prompt prompt = buildPrompt(question, file, history);
                log.info("Sending prompt to user: {}", user.getUsername());

                // 스트리밍 호출
                StringBuilder response = new StringBuilder();
                streamingChatModel.stream(prompt)
                        .subscribe(
                                chunk -> {
                                    try {
                                        String content = chunk.getResult().getOutput().getContent();
                                        response.append(content);
                                        emitter.send(SseEmitter.event().data(content));
                                    } catch (IOException e) {
                                        log.error("SSE send error", e);
                                    }
                                },
                                error -> {
                                    log.error("Chat stream error", error);
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    // DB 저장
                                    saveChatMessage(user, question, response.toString());
                                    emitter.complete();
                                }
                        );

            } catch (Exception e) {
                log.error("Chat Error", e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private Prompt buildPrompt(String question, MultipartFile file, List<ChatMessage> history) {
        StringBuilder textContent = new StringBuilder();

        // 이전 대화 맥락
        if (!history.isEmpty()) {
            textContent.append("이전 대화:\n");
            history.stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .forEach(msg -> {
                        textContent.append("Q: ").append(msg.getQuestion()).append("\n");
                        textContent.append("A: ").append(msg.getResponse()).append("\n\n");
                    });
        }

        // 현재 질문
        textContent.append("사용자 질문: ").append(question);

        // 파일 처리
        List<Media> mediaList = new ArrayList<>();

        if (file != null && !file.isEmpty()) {
            try {
                String contentType = file.getContentType();

                if (contentType != null && contentType.startsWith("image/")) {
                    // 이미지 파일 -> Media 객체로 변환
                    MimeType mimeType = MimeType.valueOf(contentType);
                    Media imageMedia = new Media(mimeType, file.getResource());
                    mediaList.add(imageMedia);
                    log.info("Image file attached: {}", file.getOriginalFilename());
                } else {
                    // 텍스트 파일 -> 프롬프트에 추가
                    String textFileContent = new String(file.getBytes());
                    textContent.append("\n\n분석할 파일 내용:\n").append(textFileContent);
                    log.info("Text file attached: {}", file.getOriginalFilename());
                }
            } catch (IOException e) {
                log.error("File processing error", e);
            }
        }

        log.info("Prompt text: {}", textContent);

        // UserMessage 생성 (이미지가 있으면 Media와 함께)
        UserMessage userMessage;
        if (!mediaList.isEmpty()) {
            userMessage = new UserMessage(textContent.toString(), mediaList);
        } else {
            userMessage = new UserMessage(textContent.toString());
        }

        return new Prompt(userMessage);
    }

    private void saveChatMessage(User user, String question, String response) {
        ChatMessage chatMessage = ChatMessage.builder()
                .user(user)
                .question(question)
                .response(response)
                .build();

        chatMessageRepository.save(chatMessage);
    }
}
