package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.portfolio.*;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "포트폴리오 API")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(
            summary = "포트폴리오 요약 조회",
            description = """
                    포트폴리오 요약 정보를 조회합니다.

                    **반환 데이터:**
                    - 총 자산 (USDT)
                    - 오늘의 손익 및 변동률
                    - 주간 손익 및 변동률
                    """
    )
    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryResponse> getPortfolioSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(portfolioService.getPortfolioSummary(userDetails.getUserId()));
    }

    @Operation(
            summary = "누적 손익 시계열 조회",
            description = """
                    기간별 누적 손익 시계열 데이터를 조회합니다.

                    **기간 옵션:**
                    - 7d, 30d, 60d, 90d, 180d
                    - custom (startDate, endDate 필수)
                    """
    )
    @GetMapping("/cumulative-profit")
    public ApiResponse<CumulativeProfitResponse> getCumulativeProfit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, custom)")
            @RequestParam(defaultValue = "7d") String period,
            @Parameter(description = "시작일 (custom일 경우 필수)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료일 (custom일 경우 필수)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(portfolioService.getCumulativeProfit(
                userDetails.getUserId(), period, startDate, endDate));
    }

    @Operation(
            summary = "월간 자산 추이 조회",
            description = """
                    월간 총 자산 추이 데이터를 조회합니다.

                    **반환 데이터:**
                    - 월초/월말 자산
                    - 월간 수익률
                    - 일별 자산 및 수익률
                    """
    )
    @GetMapping("/asset-history")
    public ApiResponse<AssetHistoryResponse> getAssetHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "연도", example = "2024")
            @RequestParam int year,
            @Parameter(description = "월", example = "1")
            @RequestParam int month) {
        return ApiResponse.success(portfolioService.getAssetHistory(userDetails.getUserId(), year, month));
    }

    @Operation(
            summary = "월간 일별 손익 조회",
            description = """
                    월간 일별 손익 데이터를 조회합니다. (캘린더용)

                    **반환 데이터:**
                    - 월간 총 손익 및 수익률
                    - 총 승/패 횟수
                    - 일별 손익 및 승/패 횟수
                    """
    )
    @GetMapping("/daily-profit")
    public ApiResponse<DailyProfitResponse> getDailyProfit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "연도", example = "2024")
            @RequestParam int year,
            @Parameter(description = "월", example = "1")
            @RequestParam int month) {
        return ApiResponse.success(portfolioService.getDailyProfit(userDetails.getUserId(), year, month));
    }

    @Operation(
            summary = "자산 분포 조회",
            description = """
                    보유 자산의 분포를 조회합니다.

                    **반환 데이터:**
                    - 순자산 총액 (USDT)
                    - 코인별 보유 수량, 평가 금액, 비중
                    """
    )
    @GetMapping("/distribution")
    public ApiResponse<AssetDistributionResponse> getAssetDistribution(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(portfolioService.getAssetDistribution(userDetails.getUserId()));
    }
}
