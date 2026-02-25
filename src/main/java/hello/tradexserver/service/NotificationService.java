package hello.tradexserver.service;

import hello.tradexserver.domain.Notification;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.NotificationType;
import hello.tradexserver.dto.response.NotificationResponse;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.NotificationRepository;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public Notification createNotification(User user, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .message(message)
                .build();
        return notificationRepository.save(notification);
    }

    public Notification createNotificationWithPosition(User user, NotificationType type, Position position, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .position(position)
                .message(message)
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findAllByUserId(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository.findUnreadByUserId(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = findNotificationById(notificationId);
        validateOwnership(notification, userId);
        notification.markAsRead();
        return NotificationResponse.from(notification);
    }

    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = findNotificationById(notificationId);
        validateOwnership(notification, userId);
        notificationRepository.delete(notification);
    }

    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    /**
     * 포지션 open/close 이벤트에서 호출.
     * DB 저장 후 WebSocket으로 실시간 push.
     * 추후 FCM push 채널도 이 메서드에서 함께 호출할 것.
     */
    public void createPositionNotification(Long userId, Long positionId,
                                           NotificationType type, String title, String message) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[NotificationService] User not found - userId: {}", userId);
            return;
        }
        Position position = positionId != null
                ? positionRepository.findById(positionId).orElse(null)
                : null;

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .position(position)
                .build();
        notificationRepository.save(notification);

        pushWebSocket(userId, NotificationResponse.from(notification));
    }

    // WebSocket: /topic/notifications/{userId} 로 실시간 push
    // TODO: 인증된 WebSocket 연결 기반으로 전환 시 /user/queue/notifications + convertAndSendToUser() 사용 권장
    private void pushWebSocket(Long userId, NotificationResponse response) {
        try {
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, response);
        } catch (Exception e) {
            log.warn("[NotificationService] WebSocket push 실패 - userId: {}", userId, e);
        }
    }

    private Notification findNotificationById(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private void validateOwnership(Notification notification, Long userId) {
        if (!notification.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }
}
