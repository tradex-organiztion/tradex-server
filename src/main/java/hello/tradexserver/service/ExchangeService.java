package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.dto.response.PositionResponse;

import java.util.List;

public interface ExchangeService {

    List<PositionResponse> getPositions(int limit);
    List<Order> getOrders();
    void subscribePositionUpdates(PositionListener listener);
}
