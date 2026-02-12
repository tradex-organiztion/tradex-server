package hello.tradexserver.controller;

import hello.tradexserver.dto.request.PositionRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
@Tag(name = "Position", description = "포지션 수동 생성/수정/삭제")
public class PositionController {

    private final PositionService positionService;

    @PostMapping
    @Operation(summary = "포지션 수동 생성", description = "포지션을 수동으로 생성합니다. 매매일지가 자동 생성됩니다.")
    public ResponseEntity<ApiResponse<PositionResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PositionRequest request
    ) {
        log.info("[PositionController] 포지션 생성 요청 - userId: {}, symbol: {}",
                userDetails.getUserId(), request.getSymbol());
        PositionResponse response = positionService.create(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{positionId}")
    @Operation(summary = "포지션 수정", description = "포지션 데이터를 수정합니다.")
    public ResponseEntity<ApiResponse<PositionResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long positionId,
            @RequestBody PositionRequest request
    ) {
        log.info("[PositionController] 포지션 수정 요청 - userId: {}, positionId: {}",
                userDetails.getUserId(), positionId);
        PositionResponse response = positionService.update(userDetails.getUserId(), positionId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{positionId}")
    @Operation(summary = "포지션 삭제", description = "포지션과 연결된 오더, 매매일지를 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long positionId
    ) {
        log.info("[PositionController] 포지션 삭제 요청 - userId: {}, positionId: {}",
                userDetails.getUserId(), positionId);
        positionService.delete(userDetails.getUserId(), positionId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}