package hello.tradexserver.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncEventListener {

    @Async
    @TransactionalEventListener
    public void onPositionClose(PositionCloseEvent event) {
        log.info("[OrderSyncListener] PositionCloseEvent 수신 - positionId: {}", event.getPositionId());
        // 오더 매핑은 PositionReconstructionService에서 오더 처리 시점에 이미 완료됨.
        // 이 리스너는 향후 포지션 종료 후 추가 작업(알림 등)에 활용 가능.
    }
}
