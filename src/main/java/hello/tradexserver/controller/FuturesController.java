package hello.tradexserver.controller;

import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.dto.request.PositionRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.dto.response.futures.*;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.FuturesService;
import hello.tradexserver.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/futures")
@RequiredArgsConstructor
@Tag(name = "Futures", description = "선물 거래 API")
public class FuturesController {

    private final FuturesService futuresService;
    private final PositionService positionService;

    // ── 통계 ──────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @Operation(summary = "선물 거래 요약 조회", description = """
            선물 거래 요약 데이터를 조회합니다.

            **반환 데이터:**
            - 총 손익 (USDT)
            - 총 거래 규모 (USDT)
            - 승률, 승/패 횟수
            - 손익 시계열 차트 데이터

            **기간 옵션:** 7d, 30d, 60d, 90d, 180d, all
            """)
    public ApiResponse<FuturesSummaryResponse> getFuturesSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, all)")
            @RequestParam(defaultValue = "30d") String period) {
        return ApiResponse.success(futuresService.getFuturesSummary(userDetails.getUserId(), period));
    }

    @GetMapping("/profit-ranking")
    @Operation(summary = "페어별 손익 랭킹 조회", description = """
            거래 페어별 손익 랭킹을 조회합니다.

            **반환 데이터:**
            - 페어별 총 손익, 거래 횟수, 승률

            **기간 옵션:** 7d, 30d, 60d, 90d, 180d, all
            """)
    public ApiResponse<ProfitRankingResponse> getProfitRanking(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, all)")
            @RequestParam(defaultValue = "30d") String period) {
        return ApiResponse.success(futuresService.getProfitRanking(userDetails.getUserId(), period));
    }

    @GetMapping("/closed-positions/summary")
    @Operation(summary = "종료 포지션 요약 조회", description = """
            종료된 포지션 요약 데이터를 조회합니다.

            **반환 데이터:**
            - 총 종료 주문 수, 승률
            - 롱/숏 포지션별 손익 및 거래 수
            """)
    public ApiResponse<ClosedPositionsSummaryResponse> getClosedPositionsSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, all)")
            @RequestParam(defaultValue = "30d") String period) {
        return ApiResponse.success(futuresService.getClosedPositionsSummary(userDetails.getUserId(), period));
    }

    @GetMapping("/closed-positions")
    @Operation(summary = "종료 포지션 목록 조회", description = """
            종료된 포지션 목록을 조회합니다.

            **필터링 옵션:** symbol, side (LONG/SHORT)
            **페이징:** page, size (기본 20), sort (기본 exitTime,desc)
            """)
    public ApiResponse<Page<ClosedPositionResponse>> getClosedPositions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "거래 페어 필터", example = "BTCUSDT")
            @RequestParam(required = false) String symbol,
            @Parameter(description = "포지션 방향 (LONG/SHORT)")
            @RequestParam(required = false) PositionSide side,
            @PageableDefault(size = 20, sort = "exitTime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(futuresService.getClosedPositions(
                userDetails.getUserId(), symbol, side, pageable));
    }

    // ── 포지션 CRUD ───────────────────────────────────────────────────────

    @PostMapping("/positions")
    @Operation(summary = "포지션 수동 생성", description = "포지션을 수동으로 생성합니다. 매매일지가 자동 생성됩니다.")
    public ResponseEntity<ApiResponse<PositionResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PositionRequest request) {
        log.info("[FuturesController] 포지션 생성 요청 - userId: {}, symbol: {}",
                userDetails.getUserId(), request.getSymbol());
        return ResponseEntity.ok(ApiResponse.success(
                positionService.create(userDetails.getUserId(), request)));
    }

    @PatchMapping("/positions/{positionId}")
    @Operation(summary = "포지션 수정", description = "포지션 데이터를 수정합니다.")
    public ResponseEntity<ApiResponse<PositionResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long positionId,
            @RequestBody PositionRequest request) {
        log.info("[FuturesController] 포지션 수정 요청 - userId: {}, positionId: {}",
                userDetails.getUserId(), positionId);
        return ResponseEntity.ok(ApiResponse.success(
                positionService.update(userDetails.getUserId(), positionId, request)));
    }

    @DeleteMapping("/positions/{positionId}")
    @Operation(summary = "포지션 삭제", description = "포지션과 연결된 오더, 매매일지를 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long positionId) {
        log.info("[FuturesController] 포지션 삭제 요청 - userId: {}, positionId: {}",
                userDetails.getUserId(), positionId);
        positionService.delete(userDetails.getUserId(), positionId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}