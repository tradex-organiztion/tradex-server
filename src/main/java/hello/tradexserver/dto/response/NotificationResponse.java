package hello.tradexserver.dto.response;

import hello.tradexserver.domain.Notification;
import hello.tradexserver.domain.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "알림 응답")
public class NotificationResponse {

    @Schema(description = "알림 ID", example = "1")
    private Long id;

    @Schema(description = "알림 유형", example = "POSITION_OPENED")
    private NotificationType type;

    @Schema(description = "알림 제목", example = "포지션 오픈")
    private String title;

    @Schema(description = "알림 메시지", example = "BTC/USDT 롱 포지션이 오픈되었습니다.")
    private String message;

    @Schema(description = "관련 포지션 ID (없으면 null)", example = "123")
    private Long positionId;

    @Schema(description = "읽음 여부", example = "false")
    private boolean isRead;

    @Schema(description = "생성 일시", example = "2025-01-23T10:30:00")
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .positionId(notification.getPosition() != null ? notification.getPosition().getId() : null)
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}