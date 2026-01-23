package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.HomeScreenResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.DailyStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@Tag(name = "Home", description = "홈 화면 API")
public class HomeController {

    private final DailyStatsService dailyStatsService;

    @Operation(
            summary = "홈 화면 요약 데이터 조회",
            description = """
                    홈 화면에 표시할 사용자의 트레이딩 요약 정보를 조회합니다.

                    **반환 데이터:**
                    - **총 자산**: 오늘/어제 총 자산, 전일 대비 증감률(%)
                    - **이번 달 수익**: 이번 달 실현 손익, 지난 달 최종 손익, 목표 달성률(%)
                    - **최근 7일 승률**: 승/패 횟수, 승률(%)
                    - **최근 7일 PnL 차트**: 일별 누적 손익 데이터
                    """
    )
    @GetMapping("/summary")
    public ApiResponse<HomeScreenResponse> getHomeScreenData(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(dailyStatsService.getHomeScreenData(userDetails.getUserId()));
    }
}
