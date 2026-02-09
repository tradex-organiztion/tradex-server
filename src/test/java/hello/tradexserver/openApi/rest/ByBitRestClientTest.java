package hello.tradexserver.openApi.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

//@Disabled("수동 테스트 - 실행하려면 @Disabled 주석 처리")
class ByBitRestClientTest {

    private ByBitRestClient byBitRestClient;

    // TODO: 실제 테스트 시 본인의 API 키로 교체
    private static final String API_KEY = "qPy3Q7DdXV6wNAm5bz";
    private static final String API_SECRET = "frwMB5wpsSHwWnXiMBKjrSOaSSLJM8J90qI4";

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        byBitRestClient = new ByBitRestClient(API_KEY, API_SECRET, restTemplate);
    }

    @Test
    void getPositionsFromExchange_실제_API_호출_테스트() {
        // given
        int limit = 50;

        // when & then - 로그로 결과 확인
        byBitRestClient.getPositions(limit);
    }

    @Test
    void getOrdersFromExchange_실제_API_호출_테스트() {
        // given
        int limit = 50;

        // when & then - 로그로 결과 확인
        byBitRestClient.getOrders(limit);
    }
}