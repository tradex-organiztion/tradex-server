package hello.tradexserver.service;

import hello.tradexserver.domain.Position;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class TradingService {

    private final PositionRepository positionRepository;

    public Page<PositionResponse> getPositions(Long userId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Position> positions;
        positions = positionRepository.findByUserId(userId, pageable);

        log.info("Returning positions from cache for user: {}", userId);
        return positions.map(PositionResponse::from);
    }

}