package hello.tradexserver.openApi.rest.order;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.enums.*;
import hello.tradexserver.openApi.rest.BybitRestClient;
import hello.tradexserver.openApi.rest.dto.BybitOrderHistory;
import hello.tradexserver.openApi.rest.dto.BybitOrderHistoryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service("BYBIT")
@RequiredArgsConstructor
public class BybitOrderService implements ExchangeOrderService {

    private final BybitRestClient bybitRestClient;

    @Override
    public List<Order> fetchAndConvertOrders(ExchangeApiKey apiKey, String symbol,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        BybitOrderHistoryData data = bybitRestClient.fetchOrderHistory(apiKey, symbol, startTime, endTime);
        if (data == null || data.getList() == null || data.getList().isEmpty()) return List.of();

        log.info("[Bybit] order/history 조회 완료 - apiKeyId: {}, 건수: {}", apiKey.getId(), data.getList().size());

        List<Order> orders = new ArrayList<>();
        for (BybitOrderHistory item : data.getList()) {
            if (!shouldSaveOrder(item)) continue;
            orders.add(convertToOrder(item, apiKey));
        }
        return orders;
    }

    private boolean shouldSaveOrder(BybitOrderHistory item) {
        String status = item.getOrderStatus();
        BigDecimal execQty = parseBigDecimal(item.getCumExecQty());

        if ("Filled".equals(status)) return true;
        if ("Cancelled".equals(status) && execQty.compareTo(BigDecimal.ZERO) > 0) return true;
        return false;
    }

    private Order convertToOrder(BybitOrderHistory item, ExchangeApiKey apiKey) {
        OrderSide side = "Buy".equalsIgnoreCase(item.getSide()) ? OrderSide.BUY : OrderSide.SELL;
        OrderType orderType = "Market".equalsIgnoreCase(item.getOrderType()) ? OrderType.MARKET : OrderType.LIMIT;
        OrderStatus status = "Filled".equals(item.getOrderStatus()) ? OrderStatus.FILLED : OrderStatus.CANCELED;
        PositionEffect positionEffect = Boolean.TRUE.equals(item.getReduceOnly())
                ? PositionEffect.CLOSE
                : PositionEffect.OPEN;

        LocalDateTime orderTime = parseTimestamp(item.getCreatedTime());
        LocalDateTime fillTime = parseTimestamp(item.getUpdatedTime());

        return Order.builder()
                .user(apiKey.getUser())
                .exchangeApiKey(apiKey)
                .exchangeName(apiKey.getExchangeName())
                .exchangeOrderId(item.getOrderId())
                .symbol(item.getSymbol())
                .side(side)
                .orderType(orderType)
                .positionEffect(positionEffect)
                .filledQuantity(parseBigDecimal(item.getCumExecQty()))
                .filledPrice(parseBigDecimal(item.getAvgPrice()))
                .cumExecFee(parseBigDecimal(item.getCumExecFee()))
                .status(status)
                .orderTime(orderTime)
                .fillTime(fillTime)
                .positionIdx(item.getPositionIdx())
                .orderLinkId(item.getOrderLinkId())
                .build();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            long millis = Long.parseLong(timestamp);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}