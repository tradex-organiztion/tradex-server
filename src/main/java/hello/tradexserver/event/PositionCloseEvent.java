package hello.tradexserver.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PositionCloseEvent {
    private final Long positionId;
}