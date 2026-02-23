package hello.tradexserver.dto.response.risk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class EntryRiskResponse {

    /** 계획 외 진입 횟수 (entryScenario 없는 포지션) */
    private int unplannedEntryCount;

    /** 계획 외 진입 비율 (%) */
    private BigDecimal unplannedEntryRate;

    /** 계획 외 진입의 승률 (%) */
    private BigDecimal unplannedEntryWinRate;

    /** 계획된 진입의 승률 (%) — 비교용 */
    private BigDecimal plannedEntryWinRate;

    /** 손절 후 즉시 재진입 횟수 (이전 PnL < 0, 15분 이내 동일 symbol 재진입) */
    private int emotionalReEntryCount;

    /** 손절 후 즉시 재진입 비율 (%) */
    private BigDecimal emotionalReEntryRate;

    /** 뇌동매매 포지션 수 (15분 내 동일 symbol 3회 이상 연속 진입 체인에 속한 포지션) */
    private int impulsiveTradeCount;

    /** 뇌동매매 비율 (%) */
    private BigDecimal impulsiveTradeRate;
}
