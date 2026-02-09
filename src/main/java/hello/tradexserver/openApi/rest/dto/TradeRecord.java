package hello.tradexserver.openApi.rest.dto;

import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.domain.enums.PositionEffect;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TradeRecord {
    private String tradeId;
    private String orderId;
    private String symbol;
    private OrderSide side;
    private PositionEffect positionEffect;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal fee;
    private String feeCurrency;
    private BigDecimal realizedPnl;
    private LocalDateTime tradeTime;
}
