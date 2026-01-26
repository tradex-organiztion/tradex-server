package hello.tradexserver.service;

import hello.tradexserver.domain.ChatMessage;
import hello.tradexserver.domain.User;
import hello.tradexserver.repository.ChatMessageRepository;
import hello.tradexserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Disabled
@SpringBootTest
class ChatServiceIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @MockitoBean
    private StreamingChatModel streamingChatModel;

    private User testUser;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();

        testUser = userRepository.findByEmail("ssetest@test.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("ssetest@test.com")
                        .username("sseTestUser")
                        .build()));
    }

    @Test
    @DisplayName("SSE 스트림 응답 확인 테스트")
    void streamChat_VerifySseEvents() throws Exception {
        // given
        String question = "안녕하세요";

        // 스트리밍 응답을 여러 청크로 나눠서 시뮬레이션
        List<ChatResponse> mockResponses = createMockStreamResponses(
                "안녕", "하세요", "! ", "무엇을 ", "도와", "드릴까요", "?"
        );

        given(streamingChatModel.stream(any(Prompt.class)))
                .willReturn(Flux.fromIterable(mockResponses));

        // when
        SseEmitter emitter = chatService.streamChat(testUser.getId(), question, null);

        // 비동기 작업 완료 대기
        Thread.sleep(2000);

        // then
        System.out.println("\n========== 테스트 결과 ==========");

        // DB에 저장된 메시지 확인
        List<ChatMessage> savedMessages = chatMessageRepository.findRecentHistory(testUser.getId(), 1);
        assertThat(savedMessages).isNotEmpty();

        ChatMessage saved = savedMessages.get(0);
        System.out.println("저장된 질문: " + saved.getQuestion());
        System.out.println("저장된 응답: " + saved.getResponse());

        assertThat(saved.getQuestion()).isEqualTo(question);
        assertThat(saved.getResponse()).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("SSE 스트림 - 실시간 이벤트 출력 테스트")
    void streamChat_PrintRealTimeEvents() throws Exception {
        // given
        String question = "오늘 날씨 어때?";

        List<ChatResponse> mockResponses = createMockStreamResponses(
                "오늘", " 날씨는", " 맑고", " 화창", "합니다", "!"
        );

        given(streamingChatModel.stream(any(Prompt.class)))
                .willReturn(Flux.fromIterable(mockResponses)
                        .delayElements(java.time.Duration.ofMillis(100))); // 100ms 딜레이로 실제 스트리밍 시뮬레이션

        CountDownLatch latch = new CountDownLatch(1);

        System.out.println("\n========== SSE 스트림 시작 ==========");
        System.out.println("질문: " + question);
        System.out.println("응답 스트리밍:");

        // when
        SseEmitter emitter = chatService.streamChat(testUser.getId(), question, null);

        emitter.onCompletion(() -> {
            System.out.println("\n[스트림 종료]");
            latch.countDown();
        });

        emitter.onError(ex -> {
            System.out.println("\n[스트림 에러]: " + ex.getMessage());
            latch.countDown();
        });

        // 완료 대기 (딜레이가 있으므로 충분히 기다림)
        latch.await(15, TimeUnit.SECONDS);

        System.out.println("========== 테스트 완료 ==========\n");
    }

    private List<ChatResponse> createMockStreamResponses(String... chunks) {
        List<ChatResponse> responses = new ArrayList<>();

        for (String chunk : chunks) {
            ChatResponse mockResponse = mock(ChatResponse.class);
            Generation mockGeneration = mock(Generation.class);
            org.springframework.ai.chat.messages.AssistantMessage mockMessage =
                    mock(org.springframework.ai.chat.messages.AssistantMessage.class);

            given(mockResponse.getResult()).willReturn(mockGeneration);
            given(mockGeneration.getOutput()).willReturn(mockMessage);
            given(mockMessage.getContent()).willReturn(chunk);

            responses.add(mockResponse);

            // 각 청크 출력
            System.out.print(chunk);
        }
        System.out.println(); // 줄바꿈

        return responses;
    }
}