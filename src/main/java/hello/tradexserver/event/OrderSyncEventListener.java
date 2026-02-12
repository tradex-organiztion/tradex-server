package hello.tradexserver.event;

import hello.tradexserver.domain.Position;
import hello.tradexserver.service.OrderMappingService;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncEventListener {

    private final OrderMappingService orderMappingService;
    private final PositionRepository positionRepository;

    @Async
    @TransactionalEventListener
    public void onPositionClose(PositionCloseEvent event) {
        log.info("[OrderSyncListener] PositionCloseEvent 수신 - positionId: {}", event.getPositionId());

        Position position = positionRepository.findById(event.getPositionId())
                .orElse(null);

        if (position == null) {
            log.warn("[OrderSyncListener] Position 조회 실패 - positionId: {}", event.getPositionId());
            return;
        }

        orderMappingService.mapOrdersToPosition(position);
    }
}