package hello.tradexserver;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;


@SpringBootTest
class TradexServerApplicationTests {

    @MockitoBean
    private StreamingChatModel streamingChatModel;

    @Test
    void contextLoads() {
    }

}
