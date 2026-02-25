package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.domain.enums.PositionEffect;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.event.PositionCloseEvent;
import hello.tradexserver.event.PositionOpenEvent;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionReconstructionService {

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final TradingJournalRepository tradingJournalRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 단건 오더 처리 — 실시간 WS용.
     * 동시성 보장을 위해 apiKeyId+symbol 기준으로 락을 건다.
     */
    public void processOrder(Order order) {
        if (order.getPosition() != null) {
            log.debug("[Reconstruction] 이미 매핑된 오더, skip - orderId: {}", order.getExchangeOrderId());
            return;
        }

        String lockKey = order.getExchangeApiKey().getId() + "_" + order.getSymbol();
        ReentrantLock lock = locks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        lock.lock();
        try {
            processOrderInternal(order);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 배치 오더 처리 — 재연결 gap fill용.
     * fillTime ASC 정렬 후 순차 처리.
     */
    public void processOrdersBatch(List<Order> orders) {
        orders.stream()
                .sorted(Comparator.comparing(Order::getFillTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(this::processOrder);
    }

    /**
     * 전체 재구성 — 서버 시작 복구용.
     * 해당 apiKey의 미매핑 오더를 시간순으로 재처리.
     */
    public void fullReconstruction(Long apiKeyId) {
        List<Order> unmappedOrders = orderRepository.findUnmappedOrdersByApiKeyId(apiKeyId);
        if (unmappedOrders.isEmpty()) {
            log.info("[Reconstruction] 미매핑 오더 없음 - apiKeyId: {}", apiKeyId);
            return;
        }
        log.info("[Reconstruction] 전체 재구성 시작 - apiKeyId: {}, {}건", apiKeyId, unmappedOrders.size());
        unmappedOrders.forEach(this::processOrder);
        log.info("[Reconstruction] 전체 재구성 완료 - apiKeyId: {}", apiKeyId);
    }

    @Transactional
    protected void processOrderInternal(Order order) {
        if (order.getPosition() != null) return;

        boolean isHedgeMode = order.getPositionIdx() != null && order.getPositionIdx() != 0;

        if (isHedgeMode) {
            PositionSide positionSide = (order.getPositionIdx() == 1) ? PositionSide.LONG : PositionSide.SHORT;
            if (order.getPositionEffect() == PositionEffect.OPEN) {
                handleEntry(order, positionSide);
            } else {
                handleExitHedge(order, positionSide);
            }
        } else {
            handleOneWayMode(order);
        }
    }

    // ============ 원웨이 모드 ============

    private void handleOneWayMode(Order order) {
        Long apiKeyId = order.getExchangeApiKey().getId();
        List<Position> openPositions = positionRepository.findOpenPositionsByApiKeyAndSymbol(
                apiKeyId, order.getSymbol());

        Position existingPosition = openPositions.isEmpty() ? null : openPositions.get(0);

        if (existingPosition == null) {
            // 기존 OPEN 포지션 없음 → 신규 진입
            PositionSide side = (order.getSide() == OrderSide.BUY) ? PositionSide.LONG : PositionSide.SHORT;
            handleEntry(order, side);
        } else {
            // 같은 방향이면 추가 진입, 반대 방향이면 청산/플립
            OrderSide entrySide = (existingPosition.getSide() == PositionSide.LONG) ? OrderSide.BUY : OrderSide.SELL;
            if (order.getSide() == entrySide) {
                handleEntry(order, existingPosition.getSide());
            } else {
                handleExitOneWay(order, existingPosition);
            }
        }
    }

    // ============ 진입 처리 ============

    private void handleEntry(Order order, PositionSide positionSide) {
        Long apiKeyId = order.getExchangeApiKey().getId();

        Optional<Position> existingOpt = positionRepository.findOpenPositionByApiKey(
                apiKeyId, order.getSymbol(), positionSide);

        if (existingOpt.isPresent()) {
            // 기존 포지션에 추가 진입
            Position existing = existingOpt.get();
            existing.addEntry(order.getFilledPrice(), order.getFilledQuantity(), order.getCumExecFee());
            order.assignToPosition(existing);
            order.correctPositionEffect(PositionEffect.OPEN);
            positionRepository.save(existing);
            orderRepository.save(order);
            log.info("[Reconstruction] 추가 진입 - positionId: {}, symbol: {}, newSize: {}",
                    existing.getId(), existing.getSymbol(), existing.getCurrentSize());
        } else {
            // 신규 포지션 생성
            Position newPosition = Position.builder()
                    .user(order.getUser())
                    .exchangeApiKey(order.getExchangeApiKey())
                    .exchangeName(order.getExchangeName())
                    .symbol(order.getSymbol())
                    .side(positionSide)
                    .avgEntryPrice(order.getFilledPrice())
                    .currentSize(order.getFilledQuantity())
                    .openFee(order.getCumExecFee())
                    .entryTime(order.getFillTime())
                    .status(PositionStatus.OPEN)
                    .build();
            positionRepository.save(newPosition);

            // 매매일지 자동 생성
            TradingJournal journal = TradingJournal.builder()
                    .position(newPosition)
                    .user(order.getUser())
                    .build();
            tradingJournalRepository.save(journal);

            order.assignToPosition(newPosition);
            order.correctPositionEffect(PositionEffect.OPEN);
            orderRepository.save(order);
            log.info("[Reconstruction] 신규 포지션 생성 - symbol: {}, side: {}, size: {}",
                    newPosition.getSymbol(), positionSide, order.getFilledQuantity());

            eventPublisher.publishEvent(PositionOpenEvent.builder()
                    .positionId(newPosition.getId())
                    .userId(order.getUser().getId())
                    .symbol(newPosition.getSymbol())
                    .side(positionSide)
                    .avgEntryPrice(newPosition.getAvgEntryPrice())
                    .leverage(newPosition.getLeverage())
                    .build());
        }
    }

    // ============ 원웨이 모드 청산 처리 ============

    private void handleExitOneWay(Order order, Position existingPosition) {
        BigDecimal remainingSize = existingPosition.getCurrentSize();
        BigDecimal exitQty = order.getFilledQuantity();
        int comparison = exitQty.compareTo(remainingSize);

        if (comparison < 0) {
            // 부분 청산
            applyPartialClose(existingPosition, order, exitQty);
        } else if (comparison == 0) {
            // 완전 청산
            applyFullClose(existingPosition, order, exitQty);
        } else {
            // 플립: exitQty > remainingSize
            BigDecimal closeQty = remainingSize;
            Order openPortion = order.splitForFlip(closeQty);
            orderRepository.save(openPortion);

            // 기존 포지션 완전 종료
            applyFullClose(existingPosition, order, closeQty);

            // 새 포지션 생성 (반대 방향)
            PositionSide newSide = (existingPosition.getSide() == PositionSide.LONG)
                    ? PositionSide.SHORT : PositionSide.LONG;
            handleEntry(openPortion, newSide);

            log.info("[Reconstruction] 포지션 플립 - {} → {}, symbol: {}, closeQty: {}, openQty: {}",
                    existingPosition.getSide(), newSide, order.getSymbol(), closeQty, openPortion.getFilledQuantity());
        }
    }

    // ============ 헷지 모드 청산 처리 ============

    private void handleExitHedge(Order order, PositionSide positionSide) {
        Long apiKeyId = order.getExchangeApiKey().getId();
        Optional<Position> existingOpt = positionRepository.findOpenPositionByApiKey(
                apiKeyId, order.getSymbol(), positionSide);

        if (existingOpt.isEmpty()) {
            log.warn("[Reconstruction] 청산 오더인데 OPEN 포지션 없음 - symbol: {}, side: {}, orderId: {}",
                    order.getSymbol(), positionSide, order.getExchangeOrderId());
            return;
        }

        Position existing = existingOpt.get();
        BigDecimal exitQty = order.getFilledQuantity();
        BigDecimal remainingSize = existing.getCurrentSize();

        if (exitQty.compareTo(remainingSize) <= 0) {
            if (exitQty.compareTo(remainingSize) == 0) {
                applyFullClose(existing, order, exitQty);
            } else {
                applyPartialClose(existing, order, exitQty);
            }
        } else {
            // 헷지 모드에서 청산량 > 잔량은 비정상 — 잔량만큼만 청산
            log.warn("[Reconstruction] 헷지 모드 청산량 초과 - positionId: {}, remaining: {}, exitQty: {}",
                    existing.getId(), remainingSize, exitQty);
            applyFullClose(existing, order, remainingSize);
        }
    }

    // ============ 부분/완전 청산 공통 ============

    private void applyPartialClose(Position position, Order order, BigDecimal exitQty) {
        position.addPartialClose(
                order.getFilledPrice(), exitQty,
                order.getCumExecFee(), order.getRealizedPnl());
        order.assignToPosition(position);
        order.correctPositionEffect(PositionEffect.CLOSE);
        positionRepository.save(position);
        orderRepository.save(order);
        log.info("[Reconstruction] 부분 청산 - positionId: {}, symbol: {}, closedQty: {}, remainingSize: {}",
                position.getId(), position.getSymbol(), exitQty, position.getCurrentSize());
    }

    private void applyFullClose(Position position, Order order, BigDecimal exitQty) {
        position.addPartialClose(
                order.getFilledPrice(), exitQty,
                order.getCumExecFee(), order.getRealizedPnl());
        position.completeClose(order.getFillTime());
        order.assignToPosition(position);
        order.correctPositionEffect(PositionEffect.CLOSE);
        positionRepository.save(position);
        orderRepository.save(order);

        log.info("[Reconstruction] 포지션 종료 - positionId: {}, symbol: {}, pnl: {}",
                position.getId(), position.getSymbol(), position.getRealizedPnl());

        eventPublisher.publishEvent(PositionCloseEvent.builder()
                .positionId(position.getId())
                .userId(position.getUser().getId())
                .symbol(position.getSymbol())
                .side(position.getSide())
                .realizedPnl(position.getRealizedPnl())
                .build());
    }
}
