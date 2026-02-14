package hello.tradexserver.openApi.rest.position;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.BinanceRestClient;
import hello.tradexserver.openApi.rest.dto.BinancePositionRisk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinancePositionRestService {

    private final BinanceRestClient binanceRestClient;

    /**
     * 현재 오픈 포지션 목록 조회 (positionAmt != 0)
     */
    public List<BinancePositionRisk> getOpenPositions(ExchangeApiKey apiKey) {
        List<BinancePositionRisk> all = binanceRestClient.fetchPositionRisk(apiKey, null);
        return all.stream()
                .filter(p -> {
                    BigDecimal amt = parseBigDecimal(p.getPositionAmt());
                    return amt.compareTo(BigDecimal.ZERO) != 0;
                })
                .toList();
    }

    /**
     * 특정 심볼의 레버리지 조회
     */
    public Integer getLeverage(ExchangeApiKey apiKey, String symbol) {
        List<BinancePositionRisk> positions = binanceRestClient.fetchPositionRisk(apiKey, symbol);
        if (positions.isEmpty()) return null;
        try {
            return Integer.parseInt(positions.get(0).getLeverage());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}