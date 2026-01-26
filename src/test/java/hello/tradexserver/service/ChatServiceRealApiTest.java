package hello.tradexserver.service;

import hello.tradexserver.domain.User;
import hello.tradexserver.repository.ChatMessageRepository;
import hello.tradexserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Disabled
@SpringBootTest
// @Disabled("실제 OpenAI API 호출 테스트 - 수동 실행 전용")
class ChatServiceRealApiTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmail("realapi-test@test.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("realapi-test@test.com")
                        .username("realApiTestUser")
                        .build()));
    }

    @Test
    @DisplayName("실제 OpenAI API 스트리밍 응답 확인")
    void streamChat_RealApi() throws Exception {
        // given
        String question = "이름이 뭐야.";

        System.out.println("\n========================================");
        System.out.println("실제 OpenAI API 스트리밍 테스트");
        System.out.println("========================================");
        System.out.println("질문: " + question);
        System.out.println("----------------------------------------");
        System.out.println("응답 스트리밍 시작...\n");

        // when
        var emitter = chatService.streamChat(testUser.getId(), question, null);

        // 스트리밍 완료 대기 (실제 API 호출이므로 충분히 기다림)
        Thread.sleep(10000);

        System.out.println("\n----------------------------------------");
        System.out.println("스트리밍 완료");

        // DB 저장 확인
        var messages = chatMessageRepository.findRecentHistory(testUser.getId(), 1);
        if (!messages.isEmpty()) {
            var saved = messages.get(0);
            System.out.println("\n[DB 저장 확인]");
            System.out.println("질문: " + saved.getQuestion());
            System.out.println("응답: " + saved.getResponse());
        }

        System.out.println("========================================\n");
    }

    @Test
    @DisplayName("실제 OpenAI API - 긴 응답 테스트")
    void streamChat_RealApi_LongResponse() throws Exception {
        // given
        String question = "비트코인이 뭔지 3문장으로 설명해줘.";

        System.out.println("\n========================================");
        System.out.println("실제 OpenAI API - 긴 응답 테스트");
        System.out.println("========================================");
        System.out.println("질문: " + question);
        System.out.println("----------------------------------------");
        System.out.println("응답 스트리밍 시작...\n");

        // when
        var emitter = chatService.streamChat(testUser.getId(), question, null);

        // 긴 응답을 위해 더 오래 대기
        Thread.sleep(15000);

        System.out.println("\n----------------------------------------");

        // DB 저장 확인
        var messages = chatMessageRepository.findRecentHistory(testUser.getId(), 1);
        if (!messages.isEmpty()) {
            var saved = messages.get(0);
            System.out.println("\n[최종 저장된 응답]");
            System.out.println(saved.getResponse());
        }

        System.out.println("========================================\n");
    }

    @Test
    @DisplayName("StreamingChatModel 직접 호출 - 실시간 스트리밍 확인")
    void streamingChatModel_DirectCall() throws Exception {
        // given
        String question = "안녕? 짧게 대답해줘.";
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullResponse = new StringBuilder();

        System.out.println("\n========================================");
        System.out.println("StreamingChatModel 직접 호출 테스트");
        System.out.println("========================================");
        System.out.println("질문: " + question);
        System.out.println("----------------------------------------");
        System.out.println("실시간 스트리밍 응답:\n");

        // when - StreamingChatModel 직접 호출
        streamingChatModel.stream(new Prompt(question))
                .subscribe(
                        chunk -> {
                            String content = chunk.getResult().getOutput().getContent();
                            if (content != null) {
                                System.out.print(content);  // 실시간 출력
                                System.out.flush();
                                fullResponse.append(content);
                            }
                        },
                        error -> {
                            System.err.println("\n에러 발생: " + error.getMessage());
                            latch.countDown();
                        },
                        () -> {
                            System.out.println("\n\n----------------------------------------");
                            System.out.println("스트리밍 완료!");
                            System.out.println("전체 응답: " + fullResponse);
                            System.out.println("========================================\n");
                            latch.countDown();
                        }
                );

        // 완료 대기
        latch.await(30, TimeUnit.SECONDS);
    }
}