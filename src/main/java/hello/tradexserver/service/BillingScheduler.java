package hello.tradexserver.service;

import hello.tradexserver.domain.Subscription;
import hello.tradexserver.domain.enums.SubscriptionPlan;
import hello.tradexserver.domain.enums.SubscriptionStatus;
import hello.tradexserver.repository.SubscriptionRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 9 * * *") // 매일 오전 9시
    @Transactional
    public void processAutoBilling() {
        List<Subscription> dueSubscriptions = subscriptionRepository
                .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.ACTIVE);

        log.info("정기결제 배치 시작 - 대상 구독 수: {}", dueSubscriptions.size());

        for (Subscription sub : dueSubscriptions) {
            if (sub.getPlan() == SubscriptionPlan.FREE || sub.getBillingKey() == null) {
                continue;
            }

            try {
                subscriptionService.chargeAndSaveHistory(sub, sub.getPlan(), sub.getUser());
                sub.updateNextBillingDate(sub.getNextBillingDate().plusMonths(1));
                subscriptionRepository.save(sub);
                log.info("정기결제 성공 - subscriptionId: {}, plan: {}", sub.getId(), sub.getPlan());
            } catch (Exception e) {
                log.error("정기결제 실패 - subscriptionId: {}, plan: {}, error: {}",
                        sub.getId(), sub.getPlan(), e.getMessage());
            }
        }

        // 해지(CANCELED) 상태에서 nextBillingDate가 오늘인 경우 → FREE 플랜으로 전환
        processCancelledSubscriptions();

        log.info("정기결제 배치 완료");
    }

    private void processCancelledSubscriptions() {
        List<Subscription> cancelledDue = subscriptionRepository
                .findByNextBillingDateAndStatus(LocalDate.now(), SubscriptionStatus.CANCELED);

        for (Subscription sub : cancelledDue) {
            sub.updatePlan(SubscriptionPlan.FREE);
            sub.updateNextBillingDate(null);
            subscriptionRepository.save(sub);
            log.info("구독 해지 완료 - FREE 전환 - subscriptionId: {}", sub.getId());
        }
    }
}