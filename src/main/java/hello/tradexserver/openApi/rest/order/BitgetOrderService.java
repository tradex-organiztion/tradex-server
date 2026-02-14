package hello.tradexserver.openApi.rest.order;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.enums.*;
import hello.tradexserver.openApi.rest.BitgetRestClient;
import hello.tradexserver.openApi.rest.dto.BitgetOrderHistoryItem;
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
@Service("BITGET")
@RequiredArgsConstructor
public class BitgetOrderService implements ExchangeOrderService {

    private final BitgetRestClient bitgetRestClient;

    @Override
    public List<Order> fetchAndConvertOrders(ExchangeApiKey apiKey, String symbol,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        Long startMillis = startTime != null ? toEpochMilli(startTime) : null;
        Long endMillis = endTime != null ? toEpochMilli(endTime) : null;

        List<BitgetOrderHistoryItem> allOrders = bitgetRestClient.fetchOrderHistory(
                apiKey, symbol, startMillis, endMillis);
        if (allOrders.isEmpty()) return List.of();

        log.info("[Bitget] orderHistory 조회 완료 - apiKeyId: {}, 건수: {}", apiKey.getId(), allOrders.size());

        List<Order> orders = new ArrayList<>();
        for (BitgetOrderHistoryItem item : allOrders) {
            if (!shouldSaveOrder(item)) continue;
            orders.add(convertToOrder(item, apiKey));
        }
        return orders;
    }

    private boolean shouldSaveOrder(BitgetOrderHistoryItem item) {
        String status = item.getStatus();
        BigDecimal execQty = parseBigDecimal(item.getBaseVolume());

        if ("filled".equals(status)) return true;
        if ("canceled".equals(status) && execQty.compareTo(BigDecimal.ZERO) > 0) return true;
        return false;
    }

    private Order convertToOrder(BitgetOrderHistoryItem item, ExchangeApiKey apiKey) {
        OrderSide side = "buy".equalsIgnoreCase(item.getSide()) ? OrderSide.BUY : OrderSide.SELL;
        OrderType orderType = "market".equalsIgnoreCase(item.getOrderType()) ? OrderType.MARKET : OrderType.LIMIT;
        OrderStatus status = "filled".equals(item.getStatus()) ? OrderStatus.FILLED : OrderStatus.CANCELED;
        PositionEffect positionEffect = convertTradeSideToPositionEffect(item.getTradeSide());
        Integer positionIdx = convertPosSideToIdx(item.getPosSide());

        BigDecimal cumFee = calculateTotalFee(item.getFeeDetail());

        LocalDateTime orderTime = parseMillisToLocalDateTime(item.getCTime());
        LocalDateTime fillTime = parseMillisToLocalDateTime(item.getUTime());

        return Order.builder()
                .user(apiKey.getUser())
                .exchangeApiKey(apiKey)
                .exchangeName(apiKey.getExchangeName())
                .exchangeOrderId(item.getOrderId())
                .symbol(item.getSymbol())
                .side(side)
                .orderType(orderType)
                .positionEffect(positionEffect)
                .filledQuantity(parseBigDecimal(item.getBaseVolume()))
                .filledPrice(parseBigDecimal(item.getPriceAvg()))
                .cumExecFee(cumFee)
                .realizedPnl(parseBigDecimal(item.getTotalProfits()))
                .status(status)
                .orderTime(orderTime)
                .fillTime(fillTime)
                .positionIdx(positionIdx)
                .build();
    }

    static PositionEffect convertTradeSideToPositionEffect(String tradeSide) {
        if (tradeSide == null) return PositionEffect.OPEN;
        // "open", "buy_single", "sell_single" → OPEN
        // 나머지 (close, reduce_*, burst_*, delivery_*, adl_*) → CLOSE
        if ("open".equals(tradeSide) || "buy_single".equals(tradeSide) || "sell_single".equals(tradeSide)) {
            return PositionEffect.OPEN;
        }
        return PositionEffect.CLOSE;
    }

    static Integer convertPosSideToIdx(String posSide) {
        if (posSide == null) return 0;
        return switch (posSide) {
            case "long" -> 1;
            case "short" -> 2;
            default -> 0; // "net"
        };
    }

    static BigDecimal calculateTotalFee(List<BitgetOrderHistoryItem.FeeDetail> feeDetail) {
        if (feeDetail == null || feeDetail.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (BitgetOrderHistoryItem.FeeDetail fd : feeDetail) {
            BigDecimal fee = parseBigDecimalStatic(fd.getFee());
            total = total.add(fee);
        }
        return total;
    }

    private BigDecimal parseBigDecimal(String value) {
        return parseBigDecimalStatic(value);
    }

    private static BigDecimal parseBigDecimalStatic(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalDateTime parseMillisToLocalDateTime(String millis) {
        if (millis == null || millis.isEmpty()) return null;
        try {
            long ms = Long.parseLong(millis);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}