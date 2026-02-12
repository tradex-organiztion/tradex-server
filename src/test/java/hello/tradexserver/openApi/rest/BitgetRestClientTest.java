package hello.tradexserver.openApi.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

/**
 * Bitget Futures REST API 테스트
 * 데모 트레이딩 API를 사용하여 API 호출을 테스트합니다.
 *
 * 데모 API 키 발급 방법:
 * 1. Bitget 웹사이트 로그인 (https://www.bitget.com)
 * 2. 우측 상단에서 "데모 트레이딩" 모드로 전환
 * 3. 우측 상단 프로필 → API → API 키 생성
 * 4. 생성된 API Key, Secret Key, Passphrase를 아래 상수에 입력
 * 5. @Disabled 주석을 제거하고 테스트 실행
 *
 * 주의사항:
 * - 반드시 **데모 모드**에서 생성한 API 키를 사용해야 합니다
 * - 프로덕션 API 키는 작동하지 않습니다 (40099 에러 발생)
 *
 * API 문서: https://www.bitget.com/api-doc/common/demotrading/restapi
 */
@Disabled("수동 테스트 - 유효한 데모 API 키 입력 후 @Disabled 주석 처리하여 실행")
class BitgetRestClientTest {

    private BitgetRestClient bitgetRestClient;

    // TODO: 실제 테스트 시 본인의 데모 API 키로 교체
    private static final String API_KEY = "bg_cc52bc8e9a70f792a38744235c090d61";
    private static final String API_SECRET = "3569674cc9b6312299f9c532400eee3131be67f30515aeba5e6ee17ae2cbc8c2";
    private static final String PASSPHRASE = "tradexApi123";
    private static final boolean IS_DEMO_MODE = true; // 데모 트레이딩 모드

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        bitgetRestClient = new BitgetRestClient(API_KEY, API_SECRET, PASSPHRASE, restTemplate, IS_DEMO_MODE);
    }

    @Test
    void getAsset() {
        // when
//        var result = bitgetRestClient.getAsset();

        // then
        System.out.println("==============================================");
        System.out.println("[Bitget Demo] Asset 조회 결과:");
//        System.out.println("Total Asset: " + result);
        System.out.println("==============================================");
    }
}