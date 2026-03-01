package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.dto.request.ExchangeApiKeyRequest;
import hello.tradexserver.dto.response.ApiKeyValidationResponse;
import hello.tradexserver.dto.response.ExchangeApiKeyResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.openApi.rest.ExchangeFactory;
import hello.tradexserver.openApi.rest.ExchangeRestClient;
import hello.tradexserver.openApi.webSocket.ExchangeWebSocketManager;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeApiKeyService {

    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final UserRepository userRepository;
    private final ExchangeWebSocketManager exchangeWebSocketManager;
    private final ExchangeFactory exchangeFactory;

    /**
     * API 키 추가
     * - 외부 API 검증을 먼저 수행하고, DB 저장은 트랜잭션으로 처리
     */
    @Transactional
    public ExchangeApiKeyResponse addApiKey(Long userId, ExchangeApiKeyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        // 동일 거래소에 이미 활성 API 키가 있는지 확인
        exchangeApiKeyRepository.findActiveByUserIdAndExchangeName(userId, request.getExchangeName())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.EXCHANGE_API_KEY_ALREADY_EXISTS);
                });

        // Bitget은 passphrase 필수
        if (request.getExchangeName() == ExchangeName.BITGET
                && (request.getPassphrase() == null || request.getPassphrase().isBlank())) {
            throw new BusinessException(ErrorCode.BITGET_PASSPHRASE_REQUIRED);
        }

        ExchangeApiKey apiKey = ExchangeApiKey.builder()
                .user(user)
                .exchangeName(request.getExchangeName())
                .apiKey(request.getApiKey())
                .apiSecret(request.getApiSecret())
                .passphrase(request.getPassphrase())
                .build();

        // API Key 유효성 검증
        ExchangeRestClient client = exchangeFactory.getExchangeService(request.getExchangeName());
        if (!client.validateApiKey(apiKey)) {
            throw new BusinessException(ErrorCode.INVALID_API_KEY);
        }

        exchangeApiKeyRepository.save(apiKey);
        log.info("API Key 추가 완료 - userId: {}, exchange: {}", userId, request.getExchangeName());

        // WebSocket 연결
        exchangeWebSocketManager.connectUser(userId, apiKey);

        return ExchangeApiKeyResponse.from(apiKey);
    }

    /**
     * 사용자의 모든 API 키 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ExchangeApiKeyResponse> getApiKeys(Long userId) {
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findByUserId(userId);
        return apiKeys.stream()
                .map(ExchangeApiKeyResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 활성 API 키 목록만 조회
     */
    @Transactional(readOnly = true)
    public List<ExchangeApiKeyResponse> getActiveApiKeys(Long userId) {
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findActiveByUserId(userId);
        return apiKeys.stream()
                .map(ExchangeApiKeyResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * API 키 삭제 (비활성화)
     */
    @Transactional
    public void deleteApiKey(Long userId, Long apiKeyId) {
        ExchangeApiKey apiKey = exchangeApiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND));

        // 소유자 확인
        if (!apiKey.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND);
        }

        // WebSocket 연결 해제
        exchangeWebSocketManager.disconnectUser(userId, apiKey.getExchangeName().name());

        // 실제 삭제
        exchangeApiKeyRepository.delete(apiKey);
        log.info("API Key 삭제 완료 - userId: {}, apiKeyId: {}, exchange: {}",
                userId, apiKeyId, apiKey.getExchangeName());
    }

    /**
     * API 키 비활성화
     */
    @Transactional
    public ExchangeApiKeyResponse deactivateApiKey(Long userId, Long apiKeyId) {
        ExchangeApiKey apiKey = exchangeApiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND));

        // 소유자 확인
        if (!apiKey.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND);
        }

        // WebSocket 연결 해제
        exchangeWebSocketManager.disconnectUser(userId, apiKey.getExchangeName().name());

        apiKey.deactivate();
        exchangeApiKeyRepository.save(apiKey);
        log.info("API Key 비활성화 - userId: {}, apiKeyId: {}", userId, apiKeyId);

        return ExchangeApiKeyResponse.from(apiKey);
    }

    /**
     * API 키 활성화
     */
    @Transactional
    public ExchangeApiKeyResponse activateApiKey(Long userId, Long apiKeyId) {
        ExchangeApiKey apiKey = exchangeApiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND));

        // 소유자 확인
        if (!apiKey.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND);
        }

        // 동일 거래소에 이미 활성 API 키가 있는지 확인
        exchangeApiKeyRepository.findActiveByUserIdAndExchangeName(userId, apiKey.getExchangeName())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(apiKeyId)) {
                        throw new BusinessException(ErrorCode.EXCHANGE_API_KEY_ALREADY_EXISTS);
                    }
                });

        apiKey.activate();
        exchangeApiKeyRepository.save(apiKey);
        log.info("API Key 활성화 - userId: {}, apiKeyId: {}", userId, apiKeyId);

        // WebSocket 연결
        exchangeWebSocketManager.connectUser(userId, apiKey);

        return ExchangeApiKeyResponse.from(apiKey);
    }

    /**
     * API 키 수정 (apiKey, apiSecret, passphrase 변경)
     * - WS 재연결 수행
     */
    @Transactional
    public ExchangeApiKeyResponse updateApiKey(Long userId, Long apiKeyId, ExchangeApiKeyRequest request) {
        ExchangeApiKey apiKey = exchangeApiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND));

        if (!apiKey.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND);
        }

        // Bitget은 passphrase 필수
        if (apiKey.getExchangeName() == ExchangeName.BITGET
                && (request.getPassphrase() == null || request.getPassphrase().isBlank())) {
            throw new BusinessException(ErrorCode.BITGET_PASSPHRASE_REQUIRED);
        }

        // 기존 WS 연결 해제 후 키 변경
        exchangeWebSocketManager.disconnectUser(userId, apiKey.getExchangeName().name());

        apiKey.update(request.getApiKey(), request.getApiSecret(), request.getPassphrase());
        exchangeApiKeyRepository.save(apiKey);
        log.info("API Key 수정 완료 - userId: {}, apiKeyId: {}, exchange: {}", userId, apiKeyId, apiKey.getExchangeName());

        // 변경된 키로 WS 재연결
        if (apiKey.getIsActive()) {
            exchangeWebSocketManager.connectUser(userId, apiKey);
        }

        return ExchangeApiKeyResponse.from(apiKey);
    }

    /**
     * 거래소별 API 키 조회
     */
    @Transactional(readOnly = true)
    public ExchangeApiKeyResponse getApiKeyByExchange(Long userId, ExchangeName exchangeName) {
        ExchangeApiKey apiKey = exchangeApiKeyRepository.findActiveByUserIdAndExchangeName(userId, exchangeName)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND));

        return ExchangeApiKeyResponse.from(apiKey);
    }

    /**
     * 단일 API 키 유효성 검증
     * - DB 조회 후 외부 API 호출이므로 트랜잭션 없이 실행
     */
    public ApiKeyValidationResponse validateApiKey(Long userId, Long apiKeyId) {
        ExchangeApiKey apiKey = exchangeApiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND));

        if (!apiKey.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EXCHANGE_API_KEY_NOT_FOUND);
        }

        ExchangeRestClient client = exchangeFactory.getExchangeService(apiKey.getExchangeName());
        boolean isValid = client.validateApiKey(apiKey);

        return ApiKeyValidationResponse.of(apiKey, isValid);
    }
}