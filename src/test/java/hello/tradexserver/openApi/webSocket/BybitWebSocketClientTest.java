package hello.tradexserver.openApi.webSocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bybit WebSocket 연결 테스트
 * 실제 API 키를 사용하여 WebSocket 연결을 테스트합니다.
 */
@Disabled("수동 테스트 - 실행하려면 @Disabled 주석 처리")
class BybitWebSocketClientTest {

    // TODO: 실제 테스트 시 본인의 API 키로 교체

    private static final String API_KEY = "8Tu8vnf519FRxaOrCw";
    private static final String API_SECRET = "WxH5O2jwD3BzjEmfeXRi82fPSzaTZmvBlvWA";
    private static final Long TEST_USER_ID = 1L;

    private BybitWebSocketClient webSocketClient;

    @BeforeEach
    void setUp() {
//        webSocketClient = new BybitWebSocketClient(TEST_USER_ID, API_KEY, API_SECRET);
    }

    @AfterEach
    void tearDown() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
    }

    @Test
    void connect_웹소켓_연결_테스트() throws InterruptedException {
        // given
        CountDownLatch latch = new CountDownLatch(1);

        // when
        webSocketClient.connect();

        // then - 연결 대기 (최대 10초)
        boolean connected = latch.await(10, TimeUnit.SECONDS);

        // 연결 시간을 주기 위해 잠시 대기
        Thread.sleep(3000);

        System.out.println("==============================================");
        System.out.println("WebSocket 연결 상태: " + webSocketClient.isConnected());
        System.out.println("==============================================");
    }

    @Test
    void connect_연결_후_메시지_수신_대기_테스트() throws InterruptedException {
        // given & when
        webSocketClient.connect();

        // then - 메시지 수신을 위해 30초 대기 (로그로 확인)
        System.out.println("==============================================");
        System.out.println("WebSocket 연결 후 메시지 수신 대기 중...");
        System.out.println("로그를 통해 인증 결과 및 메시지 확인");
        System.out.println("==============================================");

        Thread.sleep(30000);

        System.out.println("==============================================");
        System.out.println("최종 연결 상태: " + webSocketClient.isConnected());
        System.out.println("==============================================");
    }

    @Test
    void disconnect_연결_해제_테스트() throws InterruptedException {
        // given
        webSocketClient.connect();
        Thread.sleep(3000); // 연결 대기

        // when
        webSocketClient.disconnect();
        Thread.sleep(1000); // 연결 해제 대기

        // then
        assertThat(webSocketClient.isConnected()).isFalse();
        System.out.println("==============================================");
        System.out.println("WebSocket 연결 해제 완료");
        System.out.println("==============================================");
    }
}