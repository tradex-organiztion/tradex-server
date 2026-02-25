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
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Disabled
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private User user;
    private User otherUser;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@test.com")
                .username("testuser")
                .build();

        otherUser = User.builder()
                .id(2L)
                .email("other@test.com")
                .username("otheruser")
                .build();

        notification = Notification.builder()
                .id(1L)
                .user(user)
                .type(NotificationType.POSITION_ENTRY)
                .title("포지션 오픈")
                .message("BTC/USDT 롱 포지션이 오픈되었습니다.")
                .build();
    }

    @Nested
    @DisplayName("createNotification")
    class CreateNotification {

        @Test
        @DisplayName("알림 생성 성공")
        void 알림_생성_성공() {
            // given
            given(notificationRepository.save(any(Notification.class))).willReturn(notification);

            // when
            Notification result = notificationService.createNotification(
                    user, NotificationType.POSITION_ENTRY, "BTC/USDT 롱 포지션이 오픈되었습니다.");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUser()).isEqualTo(user);
            assertThat(result.getType()).isEqualTo(NotificationType.POSITION_ENTRY);
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("createNotificationWithPosition")
    class CreateNotificationWithPosition {

        @Test
        @DisplayName("포지션 포함 알림 생성 성공")
        void 포지션_포함_알림_생성_성공() {
            // given
            Position position = Position.builder().id(1L).build();
            Notification notificationWithPosition = Notification.builder()
                    .id(2L)
                    .user(user)
                    .type(NotificationType.POSITION_EXIT)
                    .position(position)
                    .message("포지션이 종료되었습니다.")
                    .build();
            given(notificationRepository.save(any(Notification.class))).willReturn(notificationWithPosition);

            // when
            Notification result = notificationService.createNotificationWithPosition(
                    user, NotificationType.POSITION_EXIT, position, "포지션이 종료되었습니다.");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getPosition()).isEqualTo(position);
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("getNotifications")
    class GetNotifications {

        @Test
        @DisplayName("사용자 알림 목록 조회 성공")
        void 사용자_알림_목록_조회_성공() {
            // given
            List<Notification> notifications = List.of(notification);
            given(notificationRepository.findAllByUserId(user.getId())).willReturn(notifications);

            // when
            List<NotificationResponse> result = notificationService.getNotifications(user.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(notification.getId());
            assertThat(result.get(0).getType()).isEqualTo(notification.getType());
            assertThat(result.get(0).getMessage()).isEqualTo(notification.getMessage());
            verify(notificationRepository).findAllByUserId(user.getId());
        }

        @Test
        @DisplayName("알림이 없으면 빈 리스트 반환")
        void 알림이_없으면_빈_리스트_반환() {
            // given
            given(notificationRepository.findAllByUserId(user.getId())).willReturn(List.of());

            // when
            List<NotificationResponse> result = notificationService.getNotifications(user.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUnreadNotifications")
    class GetUnreadNotifications {

        @Test
        @DisplayName("읽지 않은 알림 목록 조회 성공")
        void 읽지_않은_알림_목록_조회_성공() {
            // given
            List<Notification> unreadNotifications = List.of(notification);
            given(notificationRepository.findUnreadByUserId(user.getId())).willReturn(unreadNotifications);

            // when
            List<NotificationResponse> result = notificationService.getUnreadNotifications(user.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(notification.getId());
            verify(notificationRepository).findUnreadByUserId(user.getId());
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCount {

        @Test
        @DisplayName("읽지 않은 알림 개수 조회 성공")
        void 읽지_않은_알림_개수_조회_성공() {
            // given
            given(notificationRepository.countUnreadByUserId(user.getId())).willReturn(5L);

            // when
            long result = notificationService.getUnreadCount(user.getId());

            // then
            assertThat(result).isEqualTo(5L);
            verify(notificationRepository).countUnreadByUserId(user.getId());
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("알림 읽음 처리 성공")
        void 알림_읽음_처리_성공() {
            // given
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            // when
            NotificationResponse result = notificationService.markAsRead(notification.getId(), user.getId());

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(notification.getId());
            assertThat(result.isRead()).isTrue();
            assertThat(notification.isRead()).isTrue();
            verify(notificationRepository).findById(notification.getId());
        }

        @Test
        @DisplayName("알림을 찾을 수 없으면 예외 발생")
        void 알림을_찾을_수_없으면_예외_발생() {
            // given
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(999L, user.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 알림 읽음 처리 시 예외 발생")
        void 다른_사용자의_알림_읽음_처리_시_예외_발생() {
            // given
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(notification.getId(), otherUser.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("deleteNotification")
    class DeleteNotification {

        @Test
        @DisplayName("알림 삭제 성공")
        void 알림_삭제_성공() {
            // given
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            // when
            notificationService.deleteNotification(notification.getId(), user.getId());

            // then
            verify(notificationRepository).findById(notification.getId());
            verify(notificationRepository).delete(notification);
        }

        @Test
        @DisplayName("알림을 찾을 수 없으면 예외 발생")
        void 알림을_찾을_수_없으면_예외_발생() {
            // given
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationService.deleteNotification(999L, user.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);

            verify(notificationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("다른 사용자의 알림 삭제 시 예외 발생")
        void 다른_사용자의_알림_삭제_시_예외_발생() {
            // given
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            // when & then
            assertThatThrownBy(() -> notificationService.deleteNotification(notification.getId(), otherUser.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_ACCESS_DENIED);

            verify(notificationRepository, never()).delete(any());
        }
    }
}