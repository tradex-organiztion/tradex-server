package hello.tradexserver.controller;

import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.futures.*;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.FuturesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/futures")
@RequiredArgsConstructor
@Tag(name = "Futures", description = "선물 거래 API")
public class FuturesController {

    private final FuturesService futuresService;

    @Operation(
            summary = "선물 거래 요약 조회",
            description = """
                    선물 거래 요약 데이터를 조회합니다.

                    **반환 데이터:**
                    - 총 손익 (USDT)
                    - 총 거래 규모 (USDT)
                    - 승률, 승/패 횟수
                    - 손익 시계열 차트 데이터

                    **기간 옵션:**
                    - 7d, 30d, 60d, 90d, 180d, all
                    """
    )
    @GetMapping("/summary")
    public ApiResponse<FuturesSummaryResponse> getFuturesSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, all)")
            @RequestParam(defaultValue = "30d") String period) {
        return ApiResponse.success(futuresService.getFuturesSummary(userDetails.getUserId(), period));
    }

    @Operation(
            summary = "페어별 손익 랭킹 조회",
            description = """
                    거래 페어별 손익 랭킹을 조회합니다.

                    **반환 데이터:**
                    - 페어별 총 손익
                    - 거래 횟수
                    - 승률
                    """
    )
    @GetMapping("/profit-ranking")
    public ApiResponse<ProfitRankingResponse> getProfitRanking(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, all)")
            @RequestParam(defaultValue = "30d") String period) {
        return ApiResponse.success(futuresService.getProfitRanking(userDetails.getUserId(), period));
    }

    @Operation(
            summary = "종료 포지션 요약 조회",
            description = """
                    종료된 포지션 요약 데이터를 조회합니다.

                    **반환 데이터:**
                    - 총 종료 주문 수
                    - 승률
                    - 롱/숏 포지션별 손익 및 거래 수
                    """
    )
    @GetMapping("/closed-positions/summary")
    public ApiResponse<ClosedPositionsSummaryResponse> getClosedPositionsSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, all)")
            @RequestParam(defaultValue = "30d") String period) {
        return ApiResponse.success(futuresService.getClosedPositionsSummary(userDetails.getUserId(), period));
    }

    @Operation(
            summary = "종료 포지션 목록 조회",
            description = """
                    종료된 포지션 목록을 조회합니다.

                    **필터링 옵션:**
                    - symbol: 거래 페어 필터
                    - side: 포지션 방향 (LONG/SHORT)

                    **페이징:**
                    - page: 페이지 번호 (0부터 시작)
                    - size: 페이지 크기 (기본 20)
                    - sort: 정렬 기준 (기본 exitTime,desc)
                    """
    )
    @GetMapping("/closed-positions")
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
}
