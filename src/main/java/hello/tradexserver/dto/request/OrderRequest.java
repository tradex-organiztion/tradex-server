package hello.tradexserver.dto.request;

import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.domain.enums.OrderType;
import hello.tradexserver.domain.enums.PositionEffect;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotNull(message = "오더 방향은 필수입니다")
    private OrderSide side;

    @NotNull(message = "오더 타입은 필수입니다")
    private OrderType orderType;

    @NotNull(message = "주문 시간은 필수입니다")
    private LocalDateTime orderTime;

    private PositionEffect positionEffect;
    private BigDecimal filledQuantity;
    private BigDecimal filledPrice;
    private BigDecimal cumExecFee;
    private BigDecimal realizedPnl;
    private LocalDateTime fillTime;
}