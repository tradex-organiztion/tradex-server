package hello.tradexserver.service;

import hello.tradexserver.domain.ChartLayout;
import hello.tradexserver.domain.User;
import hello.tradexserver.dto.request.ChartLayoutRequest;
import hello.tradexserver.dto.response.chart.ChartLayoutContentResponse;
import hello.tradexserver.dto.response.chart.ChartLayoutMetaResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.ChartLayoutRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChartLayoutService {

    private final ChartLayoutRepository chartLayoutRepository;
    private final UserRepository userRepository;

    public Map<String, Long> create(Long userId, ChartLayoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        ChartLayout layout = ChartLayout.builder()
                .user(user)
                .name(request.getName())
                .symbol(request.getSymbol())
                .resolution(request.getResolution())
                .content(request.getContent())
                .build();

        chartLayoutRepository.save(layout);
        return Map.of("id", layout.getId());
    }

    @Transactional(readOnly = true)
    public List<ChartLayoutMetaResponse> getAll(Long userId) {
        return chartLayoutRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ChartLayoutMetaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChartLayoutContentResponse getContent(Long userId, Long id) {
        ChartLayout layout = chartLayoutRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHART_LAYOUT_NOT_FOUND));
        return ChartLayoutContentResponse.from(layout);
    }

    public void update(Long userId, Long id, ChartLayoutRequest request) {
        ChartLayout layout = chartLayoutRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHART_LAYOUT_NOT_FOUND));

        layout.update(request.getName(), request.getSymbol(), request.getResolution(), request.getContent());
    }

    public void delete(Long userId, Long id) {
        ChartLayout layout = chartLayoutRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHART_LAYOUT_NOT_FOUND));

        chartLayoutRepository.delete(layout);
    }
}
