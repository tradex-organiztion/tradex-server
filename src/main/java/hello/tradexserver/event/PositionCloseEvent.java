package hello.tradexserver.event;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PositionCloseEvent {
    private final Long positionId;
    private final Long userId;
    private final BigDecimal realizedPnl;
}
