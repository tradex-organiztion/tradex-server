package hello.tradexserver.openApi.rest.order;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;

import java.time.LocalDateTime;
import java.util.List;

public interface ExchangeOrderService {

    /**
     * 거래소 API를 호출하여 Order 목록을 조회하고 Order 엔티티로 변환하여 반환한다.
     *
     * @param apiKey    사용자의 거래소 API Key 엔티티
     * @param symbol    조회할 심볼 (null이면 전체 심볼)
     * @param startTime 조회 시작 시간
     * @param endTime   조회 종료 시간
     * @return 변환된 Order 엔티티 목록 (저장 필터 통과한 것만)
     */
    List<Order> fetchAndConvertOrders(
            ExchangeApiKey apiKey,
            String symbol,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
}