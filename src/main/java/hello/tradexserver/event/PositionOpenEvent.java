package hello.tradexserver.event;

import hello.tradexserver.domain.enums.PositionSide;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PositionOpenEvent {
    private final Long positionId;
    private final Long userId;
    private final String symbol;
    private final PositionSide side;
    private final BigDecimal avgEntryPrice;
    private final Integer leverage;
}
