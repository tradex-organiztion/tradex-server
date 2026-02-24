package hello.tradexserver.service;

import hello.tradexserver.domain.TradingPrinciple;
import hello.tradexserver.domain.User;
import hello.tradexserver.dto.request.TradingPrincipleRequest;
import hello.tradexserver.dto.response.TradingPrincipleResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.TradingPrincipleRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TradingPrincipleService {

    private final TradingPrincipleRepository tradingPrincipleRepository;
    private final UserRepository userRepository;

    public TradingPrincipleResponse create(Long userId, TradingPrincipleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        TradingPrinciple principle = TradingPrinciple.builder()
                .user(user)
                .content(request.getContent())
                .build();

        tradingPrincipleRepository.save(principle);
        return TradingPrincipleResponse.from(principle);
    }

    @Transactional(readOnly = true)
    public List<TradingPrincipleResponse> getAll(Long userId) {
        return tradingPrincipleRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TradingPrincipleResponse::from)
                .toList();
    }

    public TradingPrincipleResponse update(Long userId, Long principleId, TradingPrincipleRequest request) {
        TradingPrinciple principle = tradingPrincipleRepository.findByIdAndUserId(principleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_PRINCIPLE_NOT_FOUND));

        principle.update(request.getContent());
        return TradingPrincipleResponse.from(principle);
    }

    public void delete(Long userId, Long principleId) {
        TradingPrinciple principle = tradingPrincipleRepository.findByIdAndUserId(principleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_PRINCIPLE_NOT_FOUND));

        tradingPrincipleRepository.delete(principle);
    }
}
