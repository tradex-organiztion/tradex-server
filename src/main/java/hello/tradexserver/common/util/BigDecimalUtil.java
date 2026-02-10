package hello.tradexserver.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BigDecimalUtil {

    private BigDecimalUtil() {
    }

    /**
     * 수익률(%) 계산: (value / base) * 100
     * base가 0 이하이면 ZERO 반환
     */
    public static BigDecimal calcRate(BigDecimal value, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(base, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * null → ZERO 변환
     */
    public static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * scale(2, HALF_UP) 적용
     */
    public static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
