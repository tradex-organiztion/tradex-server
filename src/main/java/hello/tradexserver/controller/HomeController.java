package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.HomeScreenResponse;
import hello.tradexserver.dto.response.NotificationResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.DailyStatsService;
import hello.tradexserver.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@Tag(name = "Home", description = "홈 화면 API")
public class HomeController {

    private final DailyStatsService dailyStatsService;
    private final NotificationService notificationService;

    /**
     * TODO: 데이터 저장하는 과정 필요
     * - asset: Rest api로 요청이 있을 때마다 조회
     * - pnl: 포지션 close 시점에 저장
     * - 과거 데이터들: 디비에서 조회
     */
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

    @Operation(summary = "알림 목록 조회", description = "사용자의 모든 알림을 최신순으로 조회합니다.")
    @GetMapping("/notifications")
    public ApiResponse<List<NotificationResponse>> getNotifications(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(notificationService.getNotifications(userDetails.getUserId()));
    }

    @Operation(summary = "읽지 않은 알림 목록 조회", description = "사용자의 읽지 않은 알림만 최신순으로 조회합니다.")
    @GetMapping("/notifications/unread")
    public ApiResponse<List<NotificationResponse>> getUnreadNotifications(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(notificationService.getUnreadNotifications(userDetails.getUserId()));
    }

    @Operation(summary = "읽지 않은 알림 개수 조회", description = "사용자의 읽지 않은 알림 개수를 반환합니다.")
    @GetMapping("/notifications/unread-count")
    public ApiResponse<Long> getUnreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(notificationService.getUnreadCount(userDetails.getUserId()));
    }

    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<NotificationResponse> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(notificationService.markAsRead(id, userDetails.getUserId()));
    }

    @Operation(summary = "알림 삭제", description = "특정 알림을 삭제합니다.")
    @DeleteMapping("/notifications/{id}")
    public ApiResponse<Void> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.deleteNotification(id, userDetails.getUserId());
        return ApiResponse.success();
    }
}
