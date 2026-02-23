package hello.tradexserver.dto.response.risk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PositionManagementRiskResponse {

    /** 평균 손익비 R/R = avg_win / |avg_loss| */
    private BigDecimal avgRrRatio;

    /** 물타기가 발생한 포지션 수 (추가진입 시점 unrealized PnL < 0) */
    private int averagingDownCount;

    /** 물타기 비율 (추가진입이 있는 포지션 중 %) */
    private BigDecimal averagingDownRate;
}
