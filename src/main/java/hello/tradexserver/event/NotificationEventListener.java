package hello.tradexserver.event;

import hello.tradexserver.domain.enums.NotificationType;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPositionOpen(PositionOpenEvent event) {
        log.info("[NotificationEventListener] PositionOpenEvent - positionId: {}, userId: {}",
                event.getPositionId(), event.getUserId());

        String side = event.getSide() == PositionSide.LONG ? "롱" : "숏";
        String leverageText = event.getLeverage() != null ? ", 레버리지: " + event.getLeverage() + "x" : "";
        String title = "포지션 오픈";
        String message = String.format("%s %s 포지션이 오픈되었습니다. (진입가: %s%s)",
                event.getSymbol(), side, event.getAvgEntryPrice(), leverageText);

        try {
            notificationService.createPositionNotification(
                    event.getUserId(), event.getPositionId(),
                    NotificationType.POSITION_ENTRY, title, message);
        } catch (Exception e) {
            log.error("[NotificationEventListener] 포지션 오픈 알림 생성 실패 - positionId: {}, error: {}",
                    event.getPositionId(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPositionClose(PositionCloseEvent event) {
        log.info("[NotificationEventListener] PositionCloseEvent - positionId: {}, userId: {}",
                event.getPositionId(), event.getUserId());

        if (event.getRealizedPnl() == null) {
            log.warn("[NotificationEventListener] realizedPnl is null - positionId: {}", event.getPositionId());
            return;
        }

        String side = event.getSide() == PositionSide.LONG ? "롱" : "숏";
        String pnlStr = String.format("%.2f USDT", event.getRealizedPnl());
        String title = "포지션 종료";
        String message = String.format("%s %s 포지션이 종료되었습니다. (PnL: %s)",
                event.getSymbol(), side, pnlStr);

        try {
            notificationService.createPositionNotification(
                    event.getUserId(), event.getPositionId(),
                    NotificationType.POSITION_EXIT, title, message);
        } catch (Exception e) {
            log.error("[NotificationEventListener] 포지션 종료 알림 생성 실패 - positionId: {}, error: {}",
                    event.getPositionId(), e.getMessage());
        }
    }
}
