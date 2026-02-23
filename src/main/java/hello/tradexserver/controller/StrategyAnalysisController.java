package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.strategy.StrategyAnalysisResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.StrategyAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
@Tag(name = "Strategy Analysis", description = "전략 패턴 분석 API")
public class StrategyAnalysisController {

    private final StrategyAnalysisService strategyAnalysisService;

    @GetMapping("/analysis")
    @Operation(summary = "전략 패턴 분석 조회", description = """
            매매일지(지표·타임프레임·기술분석)와 포지션 결과를 바탕으로
            전략 조합별 승률·평균 손익·R/R을 집계합니다.

            같은 지표 조합은 순서에 관계없이 동일 전략으로 처리합니다.
            (예: ["RSI","MACD"] == ["MACD","RSI"])

            **기간 옵션:** 7d, 30d, 60d, 90d, 180d, all, custom
            - custom 선택 시 startDate, endDate 필수
            """)
    public ApiResponse<StrategyAnalysisResponse> analyze(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "거래소 (BYBIT 등, 미입력 시 전체)")
            @RequestParam(required = false) String exchangeName,
            @Parameter(description = "기간 (7d, 30d, 60d, 90d, 180d, all, custom)")
            @RequestParam(defaultValue = "30d") String period,
            @Parameter(description = "조회 시작일 (period=custom 시 사용, yyyy-MM-dd)")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "조회 종료일 (period=custom 시 사용, yyyy-MM-dd)")
            @RequestParam(required = false) LocalDate endDate) {

        return ApiResponse.success(
                strategyAnalysisService.analyze(
                        userDetails.getUserId(), exchangeName, period, startDate, endDate));
    }
}
