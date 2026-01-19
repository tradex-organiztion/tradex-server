package hello.tradexserver.openApi;

import hello.tradexserver.domain.Order;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.openApi.webSocket.PositionListener;

import java.util.List;

public interface ExchangeService {

    List<PositionResponse> getPositions(int limit);
    List<Order> getOrders();
    void subscribePositionUpdates(PositionListener listener);
}
