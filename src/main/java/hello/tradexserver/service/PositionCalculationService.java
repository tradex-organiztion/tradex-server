package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.PositionEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
public class PositionCalculationService {

    /**
     * 매핑된 오더 목록으로부터 포지션 필드를 계산하여 반영한다.
     * - 청산 오더: closedFee, avgExitPrice, realizedPnl
     * - 진입 오더: openFee
     */
    public void recalculateFromOrders(Position position, List<Order> orders) {
        List<Order> entryOrders = orders.stream()
                .filter(o -> o.getPositionEffect() == PositionEffect.OPEN)
                .toList();

        List<Order> exitOrders = orders.stream()
                .filter(o -> o.getPositionEffect() == PositionEffect.CLOSE)
                .toList();

        // 청산 오더 기반 계산
        BigDecimal closedSize = exitOrders.stream()
                .map(Order::getFilledQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal closedFee = exitOrders.stream()
                .map(o -> o.getCumExecFee() != null ? o.getCumExecFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgExitPrice = BigDecimal.ZERO;
        if (closedSize.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalNotional = exitOrders.stream()
                    .map(o -> o.getFilledPrice().multiply(o.getFilledQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgExitPrice = totalNotional.divide(closedSize, 8, RoundingMode.HALF_UP);
        }

        BigDecimal realizedPnl = exitOrders.stream()
                .map(o -> o.getRealizedPnl() != null ? o.getRealizedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 진입 오더 기반 계산
        BigDecimal openFee = entryOrders.stream()
                .map(o -> o.getCumExecFee() != null ? o.getCumExecFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        position.applyMappingResult(avgExitPrice, realizedPnl, closedFee, openFee);

        log.info("[PositionCalc] 재계산 완료 - positionId: {}, avgExitPrice: {}, realizedPnl: {}, openFee: {}, closedFee: {}",
                position.getId(), avgExitPrice, realizedPnl, openFee, closedFee);
    }
}