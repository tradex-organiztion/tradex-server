package hello.tradexserver.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@Schema(description = "일별 손익 차트 데이터")
public class DailyPnlChartData {

    @Schema(description = "날짜", example = "2025-01-23")
    private LocalDate date;

    @Schema(description = "해당 일자 누적 손익 (원)", example = "500000")
    private BigDecimal cumulativePnl;

    public static DailyPnlChartData of(LocalDate date, BigDecimal pnl) {
        return DailyPnlChartData.builder()
                .date(date)
                .cumulativePnl(pnl)
                .build();
    }
}
