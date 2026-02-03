package hello.tradexserver.openApi.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinBalanceDto {

    private String coin;
    private BigDecimal walletBalance;
    private BigDecimal usdValue;
}
