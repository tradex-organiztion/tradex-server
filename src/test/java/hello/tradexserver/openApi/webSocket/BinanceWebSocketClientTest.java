package hello.tradexserver.openApi.webSocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binance User Data Stream WebSocket 테스트
 * 실제 API 키를 사용하여 WebSocket 연결을 테스트합니다.
 *
 * Testnet API 키 발급: https://testnet.binancefuture.com
 *
 * 테스트 시나리오:
 * 1. WebSocket 연결 → ListenKey 생성 → 연결 확인
 * 2. ACCOUNT_UPDATE 이벤트 수신 대기 (Testnet에서 포지션 변경 시)
 * 3. 연결 해제
 */
@Disabled("수동 테스트 - 실행하려면 @Disabled 주석 처리")
class BinanceWebSocketClientTest {

    // TODO: 실제 테스트 시 본인의 Testnet API 키로 교체
    private static final String API_KEY = "your-testnet-api-key";
    private static final String API_SECRET = "your-testnet-api-secret";
    private static final Long TEST_USER_ID = 1L;

    private BinanceWebSocketClient webSocketClient;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        webSocketClient = new BinanceWebSocketClient(TEST_USER_ID, API_KEY, API_SECRET, restTemplate);
    }

    @AfterEach
    void tearDown() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
    }

    @Test
    @DisplayName("WebSocket 연결 테스트")
    void connect_웹소켓_연결_테스트() throws InterruptedException {
        // when
        webSocketClient.connect();

        // then - 연결 대기 (최대 5초)
        Thread.sleep(5000);

        System.out.println("==============================================");
        System.out.println("WebSocket 연결 상태: " + webSocketClient.isConnected());
        System.out.println("==============================================");

        assertThat(webSocketClient.isConnected()).isTrue();
    }

    @Test
    @DisplayName("WebSocket 연결 후 메시지 수신 대기 테스트")
    void connect_메시지_수신_대기_테스트() throws InterruptedException {
        // given & when
        webSocketClient.connect();

        System.out.println("==============================================");
        System.out.println("Binance User Data Stream 연결됨");
        System.out.println("ACCOUNT_UPDATE 이벤트 수신 대기 중...");
        System.out.println("");
        System.out.println("테스트 방법:");
        System.out.println("1. Binance Testnet (https://testnet.binancefuture.com) 접속");
        System.out.println("2. 포지션 오픈/클로즈 또는 주문 실행");
        System.out.println("3. 로그에서 ACCOUNT_UPDATE 이벤트 확인");
        System.out.println("==============================================");

        // 60초 동안 메시지 수신 대기
        Thread.sleep(60000);

        System.out.println("==============================================");
        System.out.println("최종 연결 상태: " + webSocketClient.isConnected());
        System.out.println("==============================================");
    }

    @Test
    @DisplayName("WebSocket 장시간 연결 테스트 (5분)")
    void connect_장시간_연결_테스트() throws InterruptedException {
        // given & when
        webSocketClient.connect();

        System.out.println("==============================================");
        System.out.println("Binance WebSocket 장시간 연결 테스트 (5분)");
        System.out.println("ListenKey 자동 연장 확인용");
        System.out.println("==============================================");

        // 5분 동안 연결 유지
        for (int i = 1; i <= 5; i++) {
            Thread.sleep(60000);
            System.out.println(i + "분 경과 - 연결 상태: " + webSocketClient.isConnected());
        }

        System.out.println("==============================================");
        System.out.println("5분 테스트 완료");
        System.out.println("최종 연결 상태: " + webSocketClient.isConnected());
        System.out.println("==============================================");

        assertThat(webSocketClient.isConnected()).isTrue();
    }

    @Test
    @DisplayName("WebSocket 연결 해제 테스트")
    void disconnect_연결_해제_테스트() throws InterruptedException {
        // given
        webSocketClient.connect();
        Thread.sleep(5000); // 연결 대기

        assertThat(webSocketClient.isConnected()).isTrue();

        // when
        webSocketClient.disconnect();
        Thread.sleep(1000); // 연결 해제 대기

        // then
        assertThat(webSocketClient.isConnected()).isFalse();

        System.out.println("==============================================");
        System.out.println("WebSocket 연결 해제 완료");
        System.out.println("==============================================");
    }

    @Test
    @DisplayName("WebSocket 재연결 테스트")
    void reconnect_재연결_테스트() throws InterruptedException {
        // given - 첫 번째 연결
        webSocketClient.connect();
        Thread.sleep(5000);
        assertThat(webSocketClient.isConnected()).isTrue();
        System.out.println("첫 번째 연결 성공");

        // when - 연결 해제 후 재연결
        webSocketClient.disconnect();
        Thread.sleep(2000);
        assertThat(webSocketClient.isConnected()).isFalse();
        System.out.println("연결 해제됨");

        // 새 클라이언트로 재연결
        webSocketClient = new BinanceWebSocketClient(TEST_USER_ID, API_KEY, API_SECRET, restTemplate);
        webSocketClient.connect();
        Thread.sleep(5000);

        // then
        assertThat(webSocketClient.isConnected()).isTrue();

        System.out.println("==============================================");
        System.out.println("재연결 성공");
        System.out.println("==============================================");
    }

    @Test
    @DisplayName("PositionListener 설정 테스트")
    void setPositionListener_테스트() throws InterruptedException {
        // given
        webSocketClient.setPositionListener(new PositionListener() {
            @Override
            public void onPositionUpdate(hello.tradexserver.domain.Position position) {
                System.out.println("==============================================");
                System.out.println("Position Update 수신!");
                System.out.println("Symbol: " + position.getSymbol());
                System.out.println("Side: " + position.getSide());
                System.out.println("Entry Price: " + position.getAvgEntryPrice());
                System.out.println("==============================================");
            }

            @Override
            public void onPositionClosed(hello.tradexserver.domain.Position position) {
                System.out.println("==============================================");
                System.out.println("Position Closed!");
                System.out.println("Symbol: " + position.getSymbol());
                System.out.println("==============================================");
            }
        });

        // when
        webSocketClient.connect();

        System.out.println("==============================================");
        System.out.println("PositionListener 설정 완료");
        System.out.println("포지션 변경 이벤트 대기 중...");
        System.out.println("==============================================");

        // then - 30초 대기
        Thread.sleep(30000);
    }
}