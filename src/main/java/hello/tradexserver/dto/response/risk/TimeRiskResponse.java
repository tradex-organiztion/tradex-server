package hello.tradexserver.dto.response.risk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
public class TimeRiskResponse {

    /**
     * 시간대별 승률 (포지션 오픈 시간 기준)
     * key: 시간대 "0" ~ "23", value: 승률 (%)
     */
    private Map<String, BigDecimal> hourlyWinRates;

    /** 상승장 승률 (%) */
    private BigDecimal uptrendWinRate;

    /** 하락장 승률 (%) */
    private BigDecimal downtrendWinRate;

    /** 횡보장 승률 (%) */
    private BigDecimal sidewaysWinRate;
}
