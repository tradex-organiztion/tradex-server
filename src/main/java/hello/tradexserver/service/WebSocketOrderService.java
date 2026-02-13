package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.OrderStatus;
import hello.tradexserver.domain.enums.PositionEffect;
import hello.tradexserver.openApi.rest.BybitRestClient;
import hello.tradexserver.openApi.rest.dto.BybitClosedPnl;
import hello.tradexserver.openApi.rest.dto.BybitClosedPnlData;
import hello.tradexserver.openApi.rest.order.ExchangeOrderService;
import hello.tradexserver.openApi.webSocket.OrderListener;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketOrderService implements OrderListener {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final Map<String, ExchangeOrderService> orderServiceMap;
    private final BybitRestClient bybitRestClient;

    /**
     * WebSocket으로 수신한 Order를 즉시 DB에 저장
     */
    @Override
    @Transactional
    public void onOrderReceived(Order order) {
        if (!shouldSaveOrder(order)) return;

        if (orderRepository.existsByExchangeOrderId(order.getExchangeOrderId())) {
            log.debug("[WSOrder] 이미 존재하는 Order - orderId: {}", order.getExchangeOrderId());
            return;
        }

        orderRepository.save(order);
        log.info("[WSOrder] Order 저장 - symbol: {}, side: {}, status: {}, orderId: {}",
                order.getSymbol(), order.getSide(), order.getStatus(), order.getExchangeOrderId());
    }

    /**
     * WebSocket 재연결 시 끊긴 구간의 Order를 REST API로 보완 조회
     */
    @Override
    @Async
    @Transactional
    public void onReconnected(ExchangeApiKey apiKey, LocalDateTime gapStartTime) {
        log.info("[WSOrder] 재연결 Gap 보완 시작 - apiKeyId: {}, exchange: {}, gapStart: {}",
                apiKey.getId(), apiKey.getExchangeName(), gapStartTime);

        String exchangeKey = apiKey.getExchangeName().name();
        ExchangeOrderService orderService = orderServiceMap.get(exchangeKey);
        if (orderService == null) {
            log.warn("[WSOrder] OrderService 없음 - exchange: {}", exchangeKey);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Binance/Bitget은 symbol 필수 → DB OPEN 포지션 symbol 기준으로 조회
        List<Order> fetched;
        if (apiKey.getExchangeName() == ExchangeName.BINANCE || apiKey.getExchangeName() == ExchangeName.BITGET) {
            fetched = fetchOrdersByOpenPositionSymbols(orderService, apiKey, gapStartTime, now);
        } else {
            fetched = orderService.fetchAndConvertOrders(apiKey, null, gapStartTime, now);
        }

        if (fetched.isEmpty()) {
            log.info("[WSOrder] Gap 보완 - 신규 Order 없음");
            return;
        }

        List<String> fetchedIds = fetched.stream()
                .map(Order::getExchangeOrderId)
                .collect(Collectors.toList());

        Set<String> existing = orderRepository.findExistingOrderIds(apiKey.getExchangeName(), fetchedIds);

        List<Order> toSave = fetched.stream()
                .filter(o -> !existing.contains(o.getExchangeOrderId()))
                .collect(Collectors.toList());

        if (toSave.isEmpty()) {
            log.info("[WSOrder] Gap 보완 - 신규 저장 대상 없음");
            return;
        }

        orderRepository.saveAll(toSave);
        log.info("[WSOrder] Gap 보완 저장 완료 - apiKeyId: {}, {}건", apiKey.getId(), toSave.size());

        // Bybit 전용: close 오더 closed-pnl 보완
        if (apiKey.getExchangeName() == ExchangeName.BYBIT) {
            List<Order> closeOrders = toSave.stream()
                    .filter(o -> o.getPositionEffect() == PositionEffect.CLOSE)
                    .collect(Collectors.toList());
            if (!closeOrders.isEmpty()) {
                supplementClosedPnl(apiKey, closeOrders, gapStartTime, now);
            }
        }
    }

    /**
     * Binance용: DB OPEN 포지션 symbol 기준으로 오더 조회
     */
    private List<Order> fetchOrdersByOpenPositionSymbols(ExchangeOrderService orderService,
                                                          ExchangeApiKey apiKey,
                                                          LocalDateTime startTime,
                                                          LocalDateTime endTime) {
        List<Position> openPositions = positionRepository.findAllOpenByApiKeyId(apiKey.getId());
        Set<String> symbols = openPositions.stream()
                .map(Position::getSymbol)
                .collect(Collectors.toSet());

        List<Order> allOrders = new java.util.ArrayList<>();
        for (String symbol : symbols) {
            List<Order> orders = orderService.fetchAndConvertOrders(apiKey, symbol, startTime, endTime);
            allOrders.addAll(orders);
        }
        return allOrders;
    }

    /**
     * REST order/history에 없는 closedPnl을 closed-pnl API로 보완
     */
    private void supplementClosedPnl(ExchangeApiKey apiKey, List<Order> closeOrders,
                                      LocalDateTime startTime, LocalDateTime endTime) {
        BybitClosedPnlData pnlData = bybitRestClient.fetchClosedPnl(apiKey, null, startTime, endTime);
        if (pnlData == null || pnlData.getList() == null || pnlData.getList().isEmpty()) {
            log.info("[WSOrder] closed-pnl 데이터 없음 - apiKeyId: {}", apiKey.getId());
            return;
        }

        // orderId → closedPnl 매핑
        Map<String, String> pnlMap = pnlData.getList().stream()
                .collect(Collectors.toMap(
                        BybitClosedPnl::getOrderId,
                        BybitClosedPnl::getClosedPnl,
                        (a, b) -> a
                ));

        int updated = 0;
        for (Order order : closeOrders) {
            String pnl = pnlMap.get(order.getExchangeOrderId());
            if (pnl != null) {
                order.updateRealizedPnl(parseBigDecimal(pnl));
                updated++;
            }
        }

        if (updated > 0) {
            orderRepository.saveAll(closeOrders);
            log.info("[WSOrder] closed-pnl 보완 완료 - apiKeyId: {}, {}건", apiKey.getId(), updated);
        }
    }

    private boolean shouldSaveOrder(Order order) {
        return order.getStatus() == OrderStatus.FILLED
                || (order.getStatus() == OrderStatus.CANCELED
                    && order.getFilledQuantity() != null
                    && order.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}