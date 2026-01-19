package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class TradingService {

    private final ExchangeFactory exchangeFactory;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final PositionRepository positionRepository;

    public Page<PositionResponse> getPositions(Long userId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Position> positions;
        positions = positionRepository.findByUser_UserId(userId, pageable);

        log.info("Returning positions from cache for user: {}", userId);
        return positions.map(PositionResponse::from);
    }

    // 사용자의 거래소에서 과거 포지션 동기화
    public void syncAllPositions(Long userId, int page, int size) {
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findByUser_UserId(userId);

        List<PositionResponse> allPositions = new ArrayList<>();

        // 각 거래소에서 포지션 조회 (API 호출)
        apiKeys.forEach(apiKey -> {

            ExchangeService exchange = exchangeFactory.getExchangeService(
                    apiKey.getExchangeName(),
                    apiKey.getApiKey(),
                    apiKey.getApiSecret()
            );

//            List<PositionResponse> positions = exchange.getPositions();
//            allPositions.addAll(positions);
        });
    }


}
