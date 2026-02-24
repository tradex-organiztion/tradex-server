package hello.tradexserver.dto.request;

import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.PositionSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PositionRequest {

    @NotNull(message = "거래소는 필수입니다")
    private ExchangeName exchangeName;

    @NotBlank(message = "종목은 필수입니다")
    private String symbol;

    @NotNull(message = "포지션 방향은 필수입니다")
    private PositionSide side;

    @NotNull(message = "평균 진입 가격은 필수입니다")
    private BigDecimal avgEntryPrice;

    @NotNull(message = "현재 수량은 필수입니다")
    private BigDecimal currentSize;

    @NotNull(message = "진입 시간은 필수입니다")
    private LocalDateTime entryTime;

    private BigDecimal avgExitPrice;
    private Integer leverage;
    private BigDecimal targetPrice;
    private BigDecimal stopLossPrice;
    private LocalDateTime exitTime;
}