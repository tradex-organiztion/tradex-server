package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMappingService {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final PositionCalculationService positionCalculationService;

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
}
