package hello.tradexserver.dto.response.risk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ExitRiskResponse {

    /** 손절가 미준수 횟수 (SL 대비 0.3% 이상 불리한 가격 청산) */
    private int slViolationCount;

    /** 손절가 미준수 비율 (SL이 설정된 포지션 중 %) */
    private BigDecimal slViolationRate;

    /**
     * 평균 손절 지연 (%)
     * SL 미준수 케이스에서 실제 청산가와 SL 간 평균 오차율
     * ex) -1.5% = SL 대비 평균 1.5% 더 불리하게 청산
     */
    private BigDecimal avgSlDelay;

    /** 조기 익절 횟수 (TP보다 불리한 가격에서 청산) */
    private int earlyTpCount;

    /** 조기 익절 비율 (TP가 설정된 포지션 중 %) */
    private BigDecimal earlyTpRate;
}
