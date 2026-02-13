package hello.tradexserver.openApi.rest.position;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.BitgetRestClient;
import hello.tradexserver.openApi.rest.dto.BitgetPositionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BitgetPositionRestService {

    private final BitgetRestClient bitgetRestClient;

    /**
     * 현재 오픈 포지션 목록 조회 (total != 0)
     */
    public List<BitgetPositionItem> getOpenPositions(ExchangeApiKey apiKey) {
        List<BitgetPositionItem> all = bitgetRestClient.fetchAllPositions(apiKey);
        return all.stream()
                .filter(p -> {
                    BigDecimal total = parseBigDecimal(p.getTotal());
                    return total.compareTo(BigDecimal.ZERO) != 0;
                })
                .toList();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}