package hello.tradexserver.openApi.rest.order;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.enums.*;
import hello.tradexserver.openApi.rest.BinanceRestClient;
import hello.tradexserver.openApi.rest.dto.BinanceAllOrderItem;
import hello.tradexserver.openApi.rest.dto.BinanceUserTrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("BINANCE")
@RequiredArgsConstructor
public class BinanceOrderService implements ExchangeOrderService {

    private final BinanceRestClient binanceRestClient;

    @Override
    public List<Order> fetchAndConvertOrders(ExchangeApiKey apiKey, String symbol,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        Long startMillis = startTime != null ? toEpochMilli(startTime) : null;
        Long endMillis = endTime != null ? toEpochMilli(endTime) : null;

        List<BinanceAllOrderItem> allOrders = binanceRestClient.fetchAllOrders(
                apiKey, symbol, startMillis, endMillis);
        if (allOrders.isEmpty()) return List.of();

        // 저장 대상 오더 필터링
        List<BinanceAllOrderItem> filtered = allOrders.stream()
                .filter(this::shouldSaveOrder)
                .toList();

        if (filtered.isEmpty()) return List.of();

        // 수수료 조회: userTrades에서 orderId별 commission 합산
        List<BinanceUserTrade> trades = binanceRestClient.fetchUserTrades(
                apiKey, symbol, startMillis, endMillis);
        Map<Long, BigDecimal> feeByOrderId = trades.stream()
                .collect(Collectors.groupingBy(
                        BinanceUserTrade::getOrderId,
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> parseBigDecimal(t.getCommission()),
                                BigDecimal::add)
                ));

        // realizedPnl도 orderId별 합산
        Map<Long, BigDecimal> pnlByOrderId = trades.stream()
                .collect(Collectors.groupingBy(
                        BinanceUserTrade::getOrderId,
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> parseBigDecimal(t.getRealizedPnl()),
                                BigDecimal::add)
                ));

        log.info("[Binance] allOrders 조회 완료 - apiKeyId: {}, 대상 {}건", apiKey.getId(), filtered.size());

        List<Order> orders = new ArrayList<>();
        for (BinanceAllOrderItem item : filtered) {
            BigDecimal fee = feeByOrderId.getOrDefault(item.getOrderId(), BigDecimal.ZERO);
            BigDecimal pnl = pnlByOrderId.getOrDefault(item.getOrderId(), BigDecimal.ZERO);
            orders.add(convertToOrder(item, apiKey, fee, pnl));
        }
        return orders;
    }

    private boolean shouldSaveOrder(BinanceAllOrderItem item) {
        String status = item.getStatus();
        BigDecimal execQty = parseBigDecimal(item.getExecutedQty());

        if ("FILLED".equals(status)) return true;
        if (("CANCELED".equals(status) || "EXPIRED".equals(status) || "EXPIRED_IN_MATCH".equals(status))
                && execQty.compareTo(BigDecimal.ZERO) > 0) return true;
        return false;
    }

    private Order convertToOrder(BinanceAllOrderItem item, ExchangeApiKey apiKey,
                                  BigDecimal cumFee, BigDecimal realizedPnl) {
        OrderSide side = "BUY".equalsIgnoreCase(item.getSide()) ? OrderSide.BUY : OrderSide.SELL;
        OrderType orderType = "MARKET".equalsIgnoreCase(item.getType()) ? OrderType.MARKET : OrderType.LIMIT;
        OrderStatus status = "FILLED".equals(item.getStatus()) ? OrderStatus.FILLED : OrderStatus.CANCELED;
        PositionEffect positionEffect = Boolean.TRUE.equals(item.getReduceOnly())
                ? PositionEffect.CLOSE : PositionEffect.OPEN;
        Integer positionIdx = convertPositionSideToIdx(item.getPositionSide());

        LocalDateTime orderTime = parseMillisToLocalDateTime(item.getTime());
        LocalDateTime fillTime = "FILLED".equals(item.getStatus())
                ? parseMillisToLocalDateTime(item.getUpdateTime()) : null;

        return Order.builder()
                .user(apiKey.getUser())
                .exchangeApiKey(apiKey)
                .exchangeName(apiKey.getExchangeName())
                .exchangeOrderId(String.valueOf(item.getOrderId()))
                .symbol(item.getSymbol())
                .side(side)
                .orderType(orderType)
                .positionEffect(positionEffect)
                .filledQuantity(parseBigDecimal(item.getExecutedQty()))
                .filledPrice(parseBigDecimal(item.getAvgPrice()))
                .cumExecFee(cumFee)
                .realizedPnl(realizedPnl)
                .status(status)
                .orderTime(orderTime)
                .fillTime(fillTime)
                .positionIdx(positionIdx)
                .build();
    }

    static Integer convertPositionSideToIdx(String positionSide) {
        if (positionSide == null) return 0;
        return switch (positionSide) {
            case "LONG" -> 1;
            case "SHORT" -> 2;
            default -> 0; // BOTH
        };
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalDateTime parseMillisToLocalDateTime(Long millis) {
        if (millis == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}