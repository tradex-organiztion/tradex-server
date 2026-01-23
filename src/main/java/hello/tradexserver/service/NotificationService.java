package hello.tradexserver.service;

import hello.tradexserver.domain.Notification;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.NotificationType;
import hello.tradexserver.dto.response.NotificationResponse;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

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
