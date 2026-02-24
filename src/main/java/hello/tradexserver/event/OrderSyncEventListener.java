package hello.tradexserver.event;

import hello.tradexserver.service.DailyStatsAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncEventListener {

    private final DailyStatsAggregationService aggregationService;

    @Async
    @TransactionalEventListener
    public void onPositionClose(PositionCloseEvent event) {
        log.info("[OrderSyncListener] PositionCloseEvent 수신 - positionId: {}", event.getPositionId());

        BigDecimal pnl = event.getRealizedPnl();
        if (pnl == null) {
            log.warn("[OrderSyncListener] realizedPnl is null - positionId: {}, 집계 스킵", event.getPositionId());
            return;
        }

        try {
            aggregationService.accumulatePnl(event.getUserId(), pnl);
        } catch (Exception e) {
            log.error("[OrderSyncListener] DailyStats 집계 실패 - positionId: {}, error: {}",
                    event.getPositionId(), e.getMessage());
        }
    }
}
