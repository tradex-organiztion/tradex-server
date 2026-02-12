package hello.tradexserver.service;

import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnmappedPositionRetryScheduler {

    private final PositionRepository positionRepository;
    private final OrderMappingService orderMappingService;

    /**
     * 10분마다 CLOSED_UNMAPPED 상태인 Position의 Order 매핑을 재시도한다.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void retryUnmappedPositions() {
        List<Position> unmapped = positionRepository.findByStatus(PositionStatus.CLOSED_UNMAPPED);
        if (unmapped.isEmpty()) return;

        log.info("[RetryScheduler] CLOSED_UNMAPPED Position 재시도 - {}건", unmapped.size());

        for (Position position : unmapped) {
            try {
                orderMappingService.mapOrdersToPosition(position);
            } catch (Exception e) {
                log.warn("[RetryScheduler] 재시도 실패 - positionId: {}", position.getId(), e);
            }
        }
    }
}