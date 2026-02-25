package hello.tradexserver.controller;

import hello.tradexserver.dto.request.ChartLayoutRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.chart.ChartLayoutContentResponse;
import hello.tradexserver.dto.response.chart.ChartLayoutMetaResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.ChartLayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chart-layouts")
@RequiredArgsConstructor
@Tag(name = "ChartLayout", description = "TradingView 차트 레이아웃 관리")
public class ChartLayoutController {

    private final ChartLayoutService chartLayoutService;

    @PostMapping
    @Operation(summary = "차트 레이아웃 생성", description = "새로운 차트 레이아웃을 저장합니다")
    public ResponseEntity<ApiResponse<Map<String, Long>>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChartLayoutRequest request
    ) {
        log.info("[ChartLayoutController] 차트 레이아웃 생성 - userId: {}", userDetails.getUserId());
        Map<String, Long> response = chartLayoutService.create(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "차트 레이아웃 목록 조회", description = "사용자의 모든 차트 레이아웃 메타 정보를 조회합니다 (content 제외)")
    public ResponseEntity<ApiResponse<List<ChartLayoutMetaResponse>>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ChartLayoutMetaResponse> response = chartLayoutService.getAll(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "차트 레이아웃 content 조회", description = "특정 차트 레이아웃의 content를 조회합니다")
    public ResponseEntity<ApiResponse<ChartLayoutContentResponse>> getContent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        log.info("[ChartLayoutController] 차트 레이아웃 content 조회 - userId: {}, id: {}", userDetails.getUserId(), id);
        ChartLayoutContentResponse response = chartLayoutService.getContent(userDetails.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "차트 레이아웃 수정", description = "차트 레이아웃을 덮어씁니다")
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ChartLayoutRequest request
    ) {
        log.info("[ChartLayoutController] 차트 레이아웃 수정 - userId: {}, id: {}", userDetails.getUserId(), id);
        chartLayoutService.update(userDetails.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "차트 레이아웃 삭제", description = "차트 레이아웃을 삭제합니다")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        log.info("[ChartLayoutController] 차트 레이아웃 삭제 - userId: {}, id: {}", userDetails.getUserId(), id);
        chartLayoutService.delete(userDetails.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
