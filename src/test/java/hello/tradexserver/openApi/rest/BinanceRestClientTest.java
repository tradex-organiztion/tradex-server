package hello.tradexserver.openApi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binance Futures REST API 테스트
 * 실제 API 키를 사용하여 API 호출을 테스트합니다.
 *
 * Testnet API 키 발급: https://testnet.binancefuture.com
 */
//@Disabled("수동 테스트 - 실행하려면 @Disabled 주석 처리")
class BinanceRestClientTest {

    private BinanceRestClient binanceRestClient;

    // TODO: 실제 테스트 시 본인의 Testnet API 키로 교체
    private static final String API_KEY = "SIKaGG37inR7XXM9cdSa8GyaSg7SmvY5dZbm5SdExIH7Jj4Ds8287QyNmUWUJS3x";
    private static final String API_SECRET = "quAd6ljrCIpJW6W2dOUJCjPY5xJceUdRJzP15OGjyBegjHnMQ9VNWXYHXnztrfwB";

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        binanceRestClient = new BinanceRestClient(API_KEY, API_SECRET, restTemplate);
    }

    @Test
    @DisplayName("ListenKey 생성 테스트")
    void createListenKey_성공() {
        // when
        String listenKey = binanceRestClient.createListenKey();

        // then
        System.out.println("==============================================");
        System.out.println("생성된 ListenKey: " + listenKey);
        System.out.println("==============================================");

        assertThat(listenKey).isNotNull();
        assertThat(listenKey).isNotEmpty();
    }

    @Test
    @DisplayName("ListenKey 연장 테스트")
    void keepAliveListenKey_성공() {
        // given - 먼저 listenKey 생성
        String listenKey = binanceRestClient.createListenKey();
        System.out.println("생성된 ListenKey: " + listenKey);

        // when & then - 연장 (예외가 발생하지 않으면 성공)
        binanceRestClient.keepAliveListenKey();

        System.out.println("==============================================");
        System.out.println("ListenKey 연장 완료");
        System.out.println("==============================================");
    }

    @Test
    @DisplayName("Position Risk 조회 테스트 - 전체")
    void getPositionRisk_전체_조회() {
        // when
        String result = binanceRestClient.getPositionRisk(null);

        // then
        System.out.println("==============================================");
        System.out.println("Position Risk (전체):");
        System.out.println(result);
        System.out.println("==============================================");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Position Risk 조회 테스트 - 특정 심볼")
    void getPositionRisk_심볼_지정() {
        // given
        String symbol = "BTCUSDT";

        // when
        String result = binanceRestClient.getPositionRisk(symbol);

        // then
        System.out.println("==============================================");
        System.out.println("Position Risk (BTCUSDT):");
        System.out.println(result);
        System.out.println("==============================================");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("User Trades 조회 테스트 - 최신순")
    void getUserTrades_조회() {
        // given
        String symbol = "BTCUSDT";
        int limit = 10;

        // when - 최신순으로 조회
        String result = binanceRestClient.getUserTrades(symbol, limit, true);

        // then
        System.out.println("==============================================");
        System.out.println("User Trades (BTCUSDT, limit=10, 최신순):");
        System.out.println(prettyPrintJson(result));
        System.out.println("==============================================");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("전체 API 연동 테스트")
    void 전체_API_연동_테스트() {
        System.out.println("==============================================");
        System.out.println("Binance Futures REST API 전체 테스트 시작");
        System.out.println("==============================================\n");

        // 1. ListenKey 생성
        System.out.println("1. ListenKey 생성");
        String listenKey = binanceRestClient.createListenKey();
        System.out.println("   결과: " + listenKey + "\n");

        // 2. Position Risk 조회
        System.out.println("2. Position Risk 조회");
        String positions = binanceRestClient.getPositionRisk(null);
        System.out.println("   결과: " + positions + "\n");

        // 3. User Trades 조회 (최신순)
        System.out.println("3. User Trades 조회 (BTCUSDT, 최신순)");
        String trades = binanceRestClient.getUserTrades("BTCUSDT", 5, true);
        System.out.println("   결과:\n" + prettyPrintJson(trades) + "\n");

        // 4. ListenKey 연장
        System.out.println("4. ListenKey 연장");
        binanceRestClient.keepAliveListenKey();
        System.out.println("   완료\n");

        System.out.println("==============================================");
        System.out.println("전체 테스트 완료");
        System.out.println("==============================================");
    }

    private String prettyPrintJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object jsonObject = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception e) {
            return json;
        }
    }
}