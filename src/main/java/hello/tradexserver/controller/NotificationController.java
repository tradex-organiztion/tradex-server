package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.NotificationResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 관리")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "전체 알림 조회", description = "사용자의 모든 알림을 최신순으로 조회합니다")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<NotificationResponse> response = notificationService.getNotifications(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/unread")
    @Operation(summary = "읽지 않은 알림 조회", description = "읽지 않은 알림 목록을 최신순으로 조회합니다")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<NotificationResponse> response = notificationService.getUnreadNotifications(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "읽지 않은 알림 개수 조회", description = "읽지 않은 알림의 개수를 반환합니다")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        long count = notificationService.getUnreadCount(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        log.info("[NotificationController] 읽음 처리 - userId: {}, notificationId: {}", userDetails.getUserId(), id);
        NotificationResponse response = notificationService.markAsRead(id, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "전체 알림 읽음 처리", description = "읽지 않은 모든 알림을 읽음 상태로 변경합니다")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("[NotificationController] 전체 읽음 처리 - userId: {}", userDetails.getUserId());
        notificationService.markAllAsRead(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "알림 삭제", description = "특정 알림을 삭제합니다")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        log.info("[NotificationController] 알림 삭제 - userId: {}, notificationId: {}", userDetails.getUserId(), id);
        notificationService.deleteNotification(id, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
