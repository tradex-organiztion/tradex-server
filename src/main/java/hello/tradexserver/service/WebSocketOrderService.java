package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.domain.enums.OrderStatus;
import hello.tradexserver.domain.enums.OrderType;
import hello.tradexserver.domain.enums.PositionEffect;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.openApi.rest.BybitRestClient;
import hello.tradexserver.openApi.rest.dto.BinancePositionRisk;
import hello.tradexserver.openApi.rest.dto.BitgetPositionItem;
import hello.tradexserver.openApi.rest.dto.BybitClosedPnl;
import hello.tradexserver.openApi.rest.dto.BybitClosedPnlData;
import hello.tradexserver.openApi.rest.dto.BybitPositionRestItem;
import hello.tradexserver.openApi.rest.order.ExchangeOrderService;
import hello.tradexserver.openApi.rest.position.BinancePositionRestService;
import hello.tradexserver.openApi.rest.position.BitgetPositionRestService;
import hello.tradexserver.openApi.rest.position.BybitPositionRestService;
import hello.tradexserver.openApi.webSocket.OrderListener;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketOrderService implements OrderListener {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final PositionReconstructionService positionReconstructionService;
    private final Map<String, ExchangeOrderService> orderServiceMap;
    private final BybitRestClient bybitRestClient;
    private final BybitPositionRestService bybitPositionRestService;
    private final BinancePositionRestService binancePositionRestService;
    private final BitgetPositionRestService bitgetPositionRestService;

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

        positionReconstructionService.processOrder(order);
    }

    /**
     * WS 연결/재연결 시 통합 보완:
     * 1. REST 포지션 조회 (심볼 수집 + 시드용)
     * 2. 오더 gap fill → processOrdersBatch (포지션 자동 생성/종료)
     * 3. REST 포지션 중 DB에 없는 것 → 가상 OPEN 오더로 시드 포지션 생성
     * 4. DB에 있는 기존 포지션 → leverage 업데이트
     *
     * @param gapStartTime null이면 첫 연결 또는 서버 재시작
     */
    @Override
    @Async
    @Transactional
    public void onReconnected(ExchangeApiKey apiKey, LocalDateTime gapStartTime) {
        ExchangeApiKey freshApiKey = exchangeApiKeyRepository.findById(apiKey.getId()).orElse(null);
        if (freshApiKey == null) {
            log.warn("[WSOrder] API Key 조회 실패 - apiKeyId: {}", apiKey.getId());
            return;
        }

        log.info("[WSOrder] 통합 보완 시작 - apiKeyId: {}, exchange: {}, gapStart: {}",
                freshApiKey.getId(), freshApiKey.getExchangeName(), gapStartTime);

        // 0. gapStartTime 결정: null이면 DB 마지막 오더 시간으로 대체
        LocalDateTime effectiveGapStart = gapStartTime;
        if (effectiveGapStart == null) {
            effectiveGapStart = orderRepository.findLastFillTimeByApiKeyId(freshApiKey.getId()).orElse(null);
            if (effectiveGapStart != null) {
                log.info("[WSOrder] DB 마지막 오더 시간 기준 보완 - gapStart: {}", effectiveGapStart);
            }
        }

        // 1. REST 포지션 조회 (심볼 수집 + 시드 생성용)
        List<SeedPositionData> restPositions = fetchRestPositions(freshApiKey);
        log.info("[WSOrder] REST 포지션 조회 완료 - {}건", restPositions.size());

        // 2. 오더 gap fill
        if (effectiveGapStart != null) {
            fetchAndProcessGapOrders(freshApiKey, effectiveGapStart, restPositions);
        } else {
            log.info("[WSOrder] 오더 gap fill 스킵 - DB에 기존 오더 없음 (첫 연동)");
        }

        // 3. REST 포지션 중 DB에 없는 것 → 시드 오더 생성 + leverage 업데이트
        supplementSeedOrders(freshApiKey, restPositions);

        log.info("[WSOrder] 통합 보완 완료 - apiKeyId: {}", freshApiKey.getId());
    }

    // ============ REST 포지션 조회 ============

    private record SeedPositionData(
            String symbol, PositionSide side, BigDecimal avgEntryPrice,
            BigDecimal currentSize, Integer leverage, Integer positionIdx
    ) {}

    private List<SeedPositionData> fetchRestPositions(ExchangeApiKey apiKey) {
        try {
            return switch (apiKey.getExchangeName()) {
                case BYBIT -> bybitPositionRestService.getOpenPositions(apiKey).stream()
                        .map(p -> new SeedPositionData(
                                p.getSymbol(),
                                convertBybitSide(p.getSide()),
                                parseBigDecimal(p.getAvgPrice()),
                                parseBigDecimal(p.getSize()),
                                parseInteger(p.getLeverage()),
                                p.getPositionIdx()
                        )).collect(Collectors.toList());
                case BINANCE -> binancePositionRestService.getOpenPositions(apiKey).stream()
                        .map(p -> new SeedPositionData(
                                p.getSymbol(),
                                convertBinanceSide(p.getPositionSide(), p.getPositionAmt()),
                                parseBigDecimal(p.getEntryPrice()),
                                parseBigDecimal(p.getPositionAmt()).abs(),
                                parseInteger(p.getLeverage()),
                                convertBinancePositionSideToIdx(p.getPositionSide())
                        )).collect(Collectors.toList());
                case BITGET -> bitgetPositionRestService.getOpenPositions(apiKey).stream()
                        .map(p -> new SeedPositionData(
                                p.getInstId(),
                                convertBitgetSide(p.getHoldSide(), p.getTotal()),
                                parseBigDecimal(p.getOpenPriceAvg()),
                                parseBigDecimal(p.getTotal()).abs(),
                                parseInteger(p.getLeverage()),
                                convertBitgetHoldSideToIdx(p.getHoldSide())
                        )).collect(Collectors.toList());
            };
        } catch (Exception e) {
            log.error("[WSOrder] REST 포지션 조회 실패 - apiKeyId: {}", apiKey.getId(), e);
            return List.of();
        }
    }

    // ============ 오더 gap fill ============

    private void fetchAndProcessGapOrders(ExchangeApiKey apiKey, LocalDateTime gapStart,
                                           List<SeedPositionData> restPositions) {
        String exchangeKey = apiKey.getExchangeName().name();
        ExchangeOrderService orderService = orderServiceMap.get(exchangeKey);
        if (orderService == null) {
            log.warn("[WSOrder] OrderService 없음 - exchange: {}", exchangeKey);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        List<Order> fetched;
        if (apiKey.getExchangeName() == ExchangeName.BINANCE || apiKey.getExchangeName() == ExchangeName.BITGET) {
            Set<String> symbols = collectAllSymbols(apiKey, restPositions);
            fetched = fetchOrdersBySymbols(orderService, apiKey, symbols, gapStart, now);
        } else {
            fetched = orderService.fetchAndConvertOrders(apiKey, null, gapStart, now);
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

        positionReconstructionService.processOrdersBatch(toSave);

        // Bybit 전용: close 오더 closed-pnl 보완
        if (apiKey.getExchangeName() == ExchangeName.BYBIT) {
            List<Order> closeOrders = toSave.stream()
                    .filter(o -> o.getPositionEffect() == PositionEffect.CLOSE)
                    .collect(Collectors.toList());
            if (!closeOrders.isEmpty()) {
                supplementClosedPnl(apiKey, closeOrders, gapStart, now);
            }
        }
    }

    /**
     * DB OPEN 포지션 심볼 + REST 포지션 심볼의 합집합 (Binance/Bitget 오더 조회용)
     */
    private Set<String> collectAllSymbols(ExchangeApiKey apiKey, List<SeedPositionData> restPositions) {
        Set<String> symbols = new HashSet<>();

        // DB OPEN 포지션 심볼
        List<Position> openPositions = positionRepository.findAllOpenByApiKeyId(apiKey.getId());
        openPositions.forEach(p -> symbols.add(p.getSymbol()));

        // REST 포지션 심볼
        restPositions.forEach(p -> symbols.add(p.symbol()));

        return symbols;
    }

    private List<Order> fetchOrdersBySymbols(ExchangeOrderService orderService, ExchangeApiKey apiKey,
                                              Set<String> symbols, LocalDateTime startTime, LocalDateTime endTime) {
        List<Order> allOrders = new ArrayList<>();
        for (String symbol : symbols) {
            List<Order> orders = orderService.fetchAndConvertOrders(apiKey, symbol, startTime, endTime);
            allOrders.addAll(orders);
        }
        return allOrders;
    }

    // ============ 시드 오더 생성 ============

    /**
     * REST 포지션 중 DB에 없는 것 → 가상 OPEN 오더로 시드 포지션 생성
     * DB에 있는 포지션 → leverage 업데이트
     */
    private void supplementSeedOrders(ExchangeApiKey apiKey, List<SeedPositionData> restPositions) {
        if (restPositions.isEmpty()) return;

        for (SeedPositionData restPos : restPositions) {
            Optional<Position> existingOpt = positionRepository.findOpenPositionByApiKey(
                    apiKey.getId(), restPos.symbol(), restPos.side());

            if (existingOpt.isPresent()) {
                // 이미 DB에 있음 → leverage만 업데이트
                Position existing = existingOpt.get();
                existing.updateLeverage(restPos.leverage());
                positionRepository.save(existing);
                log.debug("[WSOrder] 보조 업데이트 - positionId: {}, symbol: {}", existing.getId(), restPos.symbol());
            } else {
                // DB에 없음 → 가상 OPEN 오더 생성 → processOrder로 포지션 생성
                String seedOrderId = "SEED_" + apiKey.getId() + "_" + restPos.symbol() + "_" + restPos.side();

                if (orderRepository.existsByExchangeOrderId(seedOrderId)) {
                    log.debug("[WSOrder] 시드 오더 이미 존재 - {}", seedOrderId);
                    continue;
                }

                OrderSide orderSide = (restPos.side() == PositionSide.LONG) ? OrderSide.BUY : OrderSide.SELL;

                Order seedOrder = Order.builder()
                        .user(apiKey.getUser())
                        .exchangeApiKey(apiKey)
                        .exchangeName(apiKey.getExchangeName())
                        .exchangeOrderId(seedOrderId)
                        .symbol(restPos.symbol())
                        .side(orderSide)
                        .orderType(OrderType.MARKET)
                        .positionEffect(PositionEffect.OPEN)
                        .filledQuantity(restPos.currentSize())
                        .filledPrice(restPos.avgEntryPrice())
                        .cumExecFee(BigDecimal.ZERO)
                        .status(OrderStatus.FILLED)
                        .orderTime(LocalDateTime.now())
                        .fillTime(LocalDateTime.now())
                        .positionIdx(restPos.positionIdx())
                        .build();

                orderRepository.save(seedOrder);
                positionReconstructionService.processOrder(seedOrder);

                // 생성된 포지션에 leverage 업데이트
                positionRepository.findOpenPositionByApiKey(apiKey.getId(), restPos.symbol(), restPos.side())
                        .ifPresent(p -> {
                            p.updateLeverage(restPos.leverage());
                            positionRepository.save(p);
                        });

                log.info("[WSOrder] 시드 포지션 생성 - symbol: {}, side: {}, size: {}, avgEntry: {}",
                        restPos.symbol(), restPos.side(), restPos.currentSize(), restPos.avgEntryPrice());
            }
        }
    }

    // ============ Bybit closed-pnl 보완 ============

    private void supplementClosedPnl(ExchangeApiKey apiKey, List<Order> closeOrders,
                                      LocalDateTime startTime, LocalDateTime endTime) {
        BybitClosedPnlData pnlData = bybitRestClient.fetchClosedPnl(apiKey, null, startTime, endTime);
        if (pnlData == null || pnlData.getList() == null || pnlData.getList().isEmpty()) {
            log.info("[WSOrder] closed-pnl 데이터 없음 - apiKeyId: {}", apiKey.getId());
            return;
        }

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

    // ============ 헬퍼 메서드 ============

    private boolean shouldSaveOrder(Order order) {
        return order.getStatus() == OrderStatus.FILLED
                || (order.getStatus() == OrderStatus.CANCELED
                    && order.getFilledQuantity() != null
                    && order.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    private PositionSide convertBybitSide(String side) {
        return "Buy".equalsIgnoreCase(side) ? PositionSide.LONG : PositionSide.SHORT;
    }

    private PositionSide convertBinanceSide(String positionSide, String positionAmt) {
        if ("LONG".equals(positionSide)) return PositionSide.LONG;
        if ("SHORT".equals(positionSide)) return PositionSide.SHORT;
        BigDecimal amt = parseBigDecimal(positionAmt);
        return amt.compareTo(BigDecimal.ZERO) >= 0 ? PositionSide.LONG : PositionSide.SHORT;
    }

    private Integer convertBinancePositionSideToIdx(String positionSide) {
        return switch (positionSide) {
            case "LONG" -> 1;
            case "SHORT" -> 2;
            default -> 0; // BOTH = one-way
        };
    }

    private PositionSide convertBitgetSide(String holdSide, String total) {
        if ("long".equalsIgnoreCase(holdSide)) return PositionSide.LONG;
        if ("short".equalsIgnoreCase(holdSide)) return PositionSide.SHORT;
        BigDecimal amt = parseBigDecimal(total);
        return amt.compareTo(BigDecimal.ZERO) >= 0 ? PositionSide.LONG : PositionSide.SHORT;
    }

    private Integer convertBitgetHoldSideToIdx(String holdSide) {
        if ("long".equalsIgnoreCase(holdSide)) return 1;
        if ("short".equalsIgnoreCase(holdSide)) return 2;
        return 0; // net = one-way
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return null; }
    }
}
