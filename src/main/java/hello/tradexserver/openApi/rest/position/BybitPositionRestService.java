package hello.tradexserver.openApi.rest.position;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.BybitRestClient;
import hello.tradexserver.openApi.rest.dto.BybitPositionRestItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BybitPositionRestService {

    private final BybitRestClient bybitRestClient;

    public List<BybitPositionRestItem> getOpenPositions(ExchangeApiKey apiKey) {
        return bybitRestClient.fetchOpenPositions(apiKey);
    }
}