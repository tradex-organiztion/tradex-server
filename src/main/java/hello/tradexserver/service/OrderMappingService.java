package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionEffect;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMappingService {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final PositionCalculationService positionCalculationService;

    /**
     * Position에 관련 Order들을 비동기로 매핑한다.
     * - entryTime ~ exitTime 범위의 Order 조회
     * - positionEffect로 진입/청산 구분
     * - 헷지 모드는 positionIdx로 추가 필터링
     */
    @Async
    @Transactional
    public void mapOrdersToPosition(Position position) {
        log.info("[OrderMapping] 매핑 시작 - positionId: {}, symbol: {}", position.getId(), position.getSymbol());

        try {
            // 1. 후보 Order 조회 (entryTime - 1초 버퍼 ~ exitTime, 미매핑 오더만)
            List<Order> candidates = orderRepository.findOrdersForMapping(
                    position.getUser().getId(),
                    position.getExchangeApiKey().getId(),
                    position.getSymbol(),
                    position.getEntryTime().minusSeconds(1),
                    position.getExitTime()
            );

            if (candidates.isEmpty()) {
                log.warn("[OrderMapping] 후보 Order 없음 - positionId: {}", position.getId());
                position.failMapping();
                positionRepository.save(position);
                return;
            }

            // 2. 헷지 모드 여부에 따라 positionIdx 필터링
            //    positionIdx=0: one-way, 1: hedge Long, 2: hedge Short
            boolean isHedgeMode = candidates.stream()
                    .anyMatch(o -> o.getPositionIdx() != null && o.getPositionIdx() != 0);

            List<Order> filtered = isHedgeMode
                    ? filterByPositionSide(candidates, position.getSide())
                    : candidates;

            // 3. 진입/청산 Order 분리
            List<Order> entryOrders = filtered.stream()
                    .filter(o -> o.getPositionEffect() == PositionEffect.OPEN)
                    .sorted(Comparator.comparing(Order::getOrderTime))
                    .collect(Collectors.toList());

            List<Order> exitOrders = filtered.stream()
                    .filter(o -> o.getPositionEffect() == PositionEffect.CLOSE)
                    .sorted(Comparator.comparing(Order::getOrderTime))
                    .collect(Collectors.toList());

            log.info("[OrderMapping] 진입 Order {}건, 청산 Order {}건 - positionId: {}",
                    entryOrders.size(), exitOrders.size(), position.getId());

            // 4. Position 연결
            entryOrders.forEach(o -> o.assignToPosition(position));
            exitOrders.forEach(o -> o.assignToPosition(position));

            List<Order> allMapped = new java.util.ArrayList<>();
            allMapped.addAll(entryOrders);
            allMapped.addAll(exitOrders);
            orderRepository.saveAll(allMapped);

            // 5. 오더 기반 포지션 데이터 계산 및 반영
            positionCalculationService.recalculateFromOrders(position, filtered);
            positionRepository.save(position);

            log.info("[OrderMapping] 매핑 완료 - positionId: {}, 총 {}건", position.getId(), allMapped.size());

        } catch (Exception e) {
            log.error("[OrderMapping] 매핑 실패 - positionId: {}", position.getId(), e);
            position.failMapping();
            positionRepository.save(position);
        }
    }

    /**
     * 포지션에 연결된 오더가 수정되었을 때 포지션 데이터를 재계산한다.
     * Order CRUD에서 호출한다.
     */
    @Transactional
    public void recalculatePosition(Long positionId) {
        Position position = positionRepository.findById(positionId).orElse(null);
        if (position == null) {
            log.warn("[OrderMapping] 재계산 대상 Position 없음 - positionId: {}", positionId);
            return;
        }

        List<Order> orders = orderRepository.findByPositionId(positionId);
        if (orders.isEmpty()) {
            log.warn("[OrderMapping] 연결된 Order 없음 - positionId: {}", positionId);
            return;
        }

        positionCalculationService.recalculateFromOrders(position, orders);
        positionRepository.save(position);
    }

    /**
     * 헷지 모드에서 포지션 방향에 맞는 Order만 필터링
     * LONG → positionIdx = 1 (또는 0)
     * SHORT → positionIdx = 2 (또는 0)
     */
    private List<Order> filterByPositionSide(List<Order> orders, PositionSide side) {
        int targetIdx = (side == PositionSide.LONG) ? 1 : 2;
        return orders.stream()
                .filter(o -> o.getPositionIdx() == null
                        || o.getPositionIdx() == 0
                        || o.getPositionIdx() == targetIdx)
                .collect(Collectors.toList());
    }
}