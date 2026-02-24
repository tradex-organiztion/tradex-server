package hello.tradexserver.domain.enums;

public enum DataSource {
    EXCHANGE,  // WebSocket/REST로 수집된 거래소 데이터 (불변)
    MANUAL     // 사용자가 직접 입력한 데이터 (수정 가능)
}