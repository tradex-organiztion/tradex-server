package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.risk.RiskAnalysisResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.RiskAnalysisService;
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
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@Tag(name = "Risk Analysis", description = "매매 리스크 분석 API")
public class RiskAnalysisController {

    private final RiskAnalysisService riskAnalysisService;

    @GetMapping("/analysis")
    @Operation(summary = "리스크 분석 조회", description = """
            매매일지를 바탕으로 5가지 카테고리의 리스크를 분석합니다.

            **분석 항목:**
            - 진입 리스크: 계획 외 진입, 감정적 재진입, 뇌동매매
            - 청산 리스크: 손절가 미준수, 평균 손절 지연, 조기 익절
            - 포지션 관리 리스크: 평균 손익비(R/R), 물타기 빈도
            - 시간/상황 리스크: 시간대별 승률, 시장 상황별 승률
            - 감정 리스크: 감정 매매, 과신 진입, 손절 후 역포지션

            **기간 옵션:** 7d, 30d, 60d, 90d, 180d, all, custom
            - custom 선택 시 startDate, endDate 필수
            """)
    public ApiResponse<RiskAnalysisResponse> analyze(
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
                riskAnalysisService.analyze(
                        userDetails.getUserId(), exchangeName, period, startDate, endDate));
    }
}
