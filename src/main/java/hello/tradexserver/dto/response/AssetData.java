package hello.tradexserver.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AssetData {
    private BigDecimal currentTotalAsset;
    private BigDecimal yesterdayTotalAsset;
    private BigDecimal changeRate;

    public static AssetData of(BigDecimal asset, BigDecimal yesterdayTotalAsset, BigDecimal rate) {
        return AssetData.builder()
                .currentTotalAsset(asset)
                .yesterdayTotalAsset(yesterdayTotalAsset)
                .changeRate(rate)
                .build();
    }
}
