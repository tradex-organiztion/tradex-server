package hello.tradexserver.controller;

import hello.tradexserver.dto.request.TradingPrincipleRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.TradingPrincipleResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.TradingPrincipleService;
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
@RequestMapping("/api/trading-principles")
@RequiredArgsConstructor
@Tag(name = "TradingPrinciple", description = "매매 원칙 관리")
public class TradingPrincipleController {

    private final TradingPrincipleService tradingPrincipleService;

    @PostMapping
    @Operation(summary = "매매 원칙 추가", description = "새로운 매매 원칙을 추가합니다")
    public ResponseEntity<ApiResponse<TradingPrincipleResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TradingPrincipleRequest request
    ) {
        log.info("[TradingPrincipleController] 매매 원칙 추가 - userId: {}", userDetails.getUserId());
        TradingPrincipleResponse response = tradingPrincipleService.create(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "매매 원칙 목록 조회", description = "사용자의 모든 매매 원칙을 조회합니다")
    public ResponseEntity<ApiResponse<List<TradingPrincipleResponse>>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<TradingPrincipleResponse> response = tradingPrincipleService.getAll(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{principleId}")
    @Operation(summary = "매매 원칙 수정", description = "매매 원칙 내용을 수정합니다")
    public ResponseEntity<ApiResponse<TradingPrincipleResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long principleId,
            @Valid @RequestBody TradingPrincipleRequest request
    ) {
        log.info("[TradingPrincipleController] 매매 원칙 수정 - userId: {}, principleId: {}",
                userDetails.getUserId(), principleId);
        TradingPrincipleResponse response = tradingPrincipleService.update(userDetails.getUserId(), principleId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{principleId}")
    @Operation(summary = "매매 원칙 삭제", description = "매매 원칙을 삭제합니다")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long principleId
    ) {
        log.info("[TradingPrincipleController] 매매 원칙 삭제 - userId: {}, principleId: {}",
                userDetails.getUserId(), principleId);
        tradingPrincipleService.delete(userDetails.getUserId(), principleId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
