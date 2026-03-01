package hello.tradexserver.controller;

import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.dto.request.ExchangeApiKeyRequest;
import hello.tradexserver.dto.response.ApiKeyValidationResponse;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.ExchangeApiKeyResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.ExchangeApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/exchange-keys")
@RequiredArgsConstructor
@Tag(name = "Exchange API Key", description = "거래소 API 키 관리")
public class ExchangeApiKeyController {

    private final ExchangeApiKeyService exchangeApiKeyService;

    @Operation(summary = "API 키 추가", description = "새로운 거래소 API 키를 추가합니다"+
            "ExchangeName: BYBIT, BINANCE, BITGET" +
            "BYBIT, BINANCE는 apiKey, apiSecret이 필요," +
            "BITGET은 passphrase 값이 추가로 필요합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ExchangeApiKeyResponse>> addApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ExchangeApiKeyRequest request
    ) {
        log.info("API Key 추가 요청 - userId: {}, exchange: {}", userDetails.getUserId(), request.getExchangeName());
        ExchangeApiKeyResponse response = exchangeApiKeyService.addApiKey(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "API 키 수정", description = "거래소 API 키와 시크릿을 수정합니다. WS가 재연결됩니다.")
    @PatchMapping("/{apiKeyId}")
    public ResponseEntity<ApiResponse<ExchangeApiKeyResponse>> updateApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long apiKeyId,
            @Valid @RequestBody ExchangeApiKeyRequest request
    ) {
        log.info("API Key 수정 요청 - userId: {}, apiKeyId: {}", userDetails.getUserId(), apiKeyId);
        ExchangeApiKeyResponse response = exchangeApiKeyService.updateApiKey(userDetails.getUserId(), apiKeyId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "API 키 목록 조회", description = "사용자의 모든 API 키 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExchangeApiKeyResponse>>> getApiKeys(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ExchangeApiKeyResponse> response = exchangeApiKeyService.getApiKeys(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "활성 API 키 목록 조회", description = "사용자의 활성 API 키 목록만 조회합니다")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ExchangeApiKeyResponse>>> getActiveApiKeys(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ExchangeApiKeyResponse> response = exchangeApiKeyService.getActiveApiKeys(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "거래소별 API 키 조회", description = "특정 거래소의 API 키를 조회합니다")
    @GetMapping("/exchange/{exchangeName}")
    public ResponseEntity<ApiResponse<ExchangeApiKeyResponse>> getApiKeyByExchange(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable ExchangeName exchangeName
    ) {
        ExchangeApiKeyResponse response = exchangeApiKeyService.getApiKeyByExchange(userDetails.getUserId(), exchangeName);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "API 키 삭제", description = "API 키를 완전히 삭제합니다")
    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<ApiResponse<Void>> deleteApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long apiKeyId
    ) {
        log.info("API Key 삭제 요청 - userId: {}, apiKeyId: {}", userDetails.getUserId(), apiKeyId);
        exchangeApiKeyService.deleteApiKey(userDetails.getUserId(), apiKeyId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "특정 API 키 유효성 검증", description = "특정 거래소 API 키가 유효한지 검증합니다")
    @GetMapping("/{apiKeyId}/validate")
    public ResponseEntity<ApiResponse<ApiKeyValidationResponse>> validateApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long apiKeyId
    ) {
        log.info("API Key 검증 요청 - userId: {}, apiKeyId: {}", userDetails.getUserId(), apiKeyId);
        ApiKeyValidationResponse response = exchangeApiKeyService.validateApiKey(userDetails.getUserId(), apiKeyId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "API 키 비활성화", description = "API 키를 비활성화합니다 (WebSocket 연결 해제)")
    @PatchMapping("/{apiKeyId}/deactivate")
    public ResponseEntity<ApiResponse<ExchangeApiKeyResponse>> deactivateApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long apiKeyId
    ) {
        log.info("API Key 비활성화 요청 - userId: {}, apiKeyId: {}", userDetails.getUserId(), apiKeyId);
        ExchangeApiKeyResponse response = exchangeApiKeyService.deactivateApiKey(userDetails.getUserId(), apiKeyId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "API 키 활성화", description = "비활성화된 API 키를 다시 활성화합니다 (WebSocket 연결)")
    @PatchMapping("/{apiKeyId}/activate")
    public ResponseEntity<ApiResponse<ExchangeApiKeyResponse>> activateApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long apiKeyId
    ) {
        log.info("API Key 활성화 요청 - userId: {}, apiKeyId: {}", userDetails.getUserId(), apiKeyId);
        ExchangeApiKeyResponse response = exchangeApiKeyService.activateApiKey(userDetails.getUserId(), apiKeyId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}