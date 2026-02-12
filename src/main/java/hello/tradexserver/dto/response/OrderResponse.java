package hello.tradexserver.dto.response;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.domain.enums.OrderStatus;
import hello.tradexserver.domain.enums.OrderType;
import hello.tradexserver.domain.enums.PositionEffect;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderResponse {

    private Long orderId;
    private OrderSide side;
    private OrderType orderType;
    private PositionEffect positionEffect;
    private BigDecimal filledQuantity;
    private BigDecimal filledPrice;
    private BigDecimal cumExecFee;
    private BigDecimal realizedPnl;
    private OrderStatus status;
    private LocalDateTime orderTime;
    private LocalDateTime fillTime;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .positionEffect(order.getPositionEffect())
                .filledQuantity(order.getFilledQuantity())
                .filledPrice(order.getFilledPrice())
                .cumExecFee(order.getCumExecFee())
                .realizedPnl(order.getRealizedPnl())
                .status(order.getStatus())
                .orderTime(order.getOrderTime())
                .fillTime(order.getFillTime())
                .createdAt(order.getCreatedAt())
                .build();
    }
}