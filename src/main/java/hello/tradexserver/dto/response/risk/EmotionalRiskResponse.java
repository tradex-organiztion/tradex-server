package hello.tradexserver.dto.response.risk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class EmotionalRiskResponse {

    /**
     * 감정 매매 횟수 (= 손절 후 즉시 재진입)
     * EntryRiskResponse.emotionalReEntryCount와 동일한 값, 항목명만 다름
     */
    private int emotionalTradeCount;

    /** 감정 매매 비율 (%) */
    private BigDecimal emotionalTradeRate;

    /** 과신 진입 횟수 (직전 익절 후 15분 내 재진입 → 손실) */
    private int overconfidentEntryCount;

    /** 과신 진입 비율 (%) */
    private BigDecimal overconfidentEntryRate;

    /** 손절 후 즉시 역포지션 횟수 (직전 손절 후 15분 내 동일 symbol 반대 방향 진입) */
    private int immediateReverseCount;

    /** 손절 후 즉시 역포지션 비율 (%) */
    private BigDecimal immediateReverseRate;
}
