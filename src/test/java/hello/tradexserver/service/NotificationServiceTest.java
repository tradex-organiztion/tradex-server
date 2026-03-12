package hello.tradexserver.service;

import hello.tradexserver.domain.Notification;
import hello.tradexserver.domain.NotificationSetting;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.NotificationType;
import hello.tradexserver.dto.request.NotificationSettingRequest;
import hello.tradexserver.dto.response.NotificationResponse;
import hello.tradexserver.dto.response.NotificationSettingResponse;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.NotificationRepository;
import hello.tradexserver.repository.NotificationSettingRepository;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private UserRepository userRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

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
            given(notificationRepository.save(any(Notification.class))).willReturn(notification);

            Notification result = notificationService.createNotification(
                    user, NotificationType.POSITION_ENTRY, "BTC/USDT 롱 포지션이 오픈되었습니다.");

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
            Position position = Position.builder().id(1L).build();
            Notification notificationWithPosition = Notification.builder()
                    .id(2L)
                    .user(user)
                    .type(NotificationType.POSITION_EXIT)
                    .position(position)
                    .message("포지션이 종료되었습니다.")
                    .build();
            given(notificationRepository.save(any(Notification.class))).willReturn(notificationWithPosition);

            Notification result = notificationService.createNotificationWithPosition(
                    user, NotificationType.POSITION_EXIT, position, "포지션이 종료되었습니다.");

            assertThat(result).isNotNull();
            assertThat(result.getPosition()).isEqualTo(position);
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("createPositionNotification - 알림 설정 필터링")
    class CreatePositionNotification {

        @Test
        @DisplayName("설정이 없으면 기본값(활성)으로 알림을 저장한다")
        void 설정_없으면_기본값_활성으로_알림_저장() {
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.empty());
            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(notificationRepository.save(any())).willReturn(notification);

            notificationService.createPositionNotification(
                    user.getId(), null, NotificationType.POSITION_ENTRY, "포지션 오픈", "메시지");

            verify(notificationRepository).save(any());
        }

        @Test
        @DisplayName("포지션 진입 알림이 활성이면 저장한다")
        void 포지션_진입_알림_활성이면_저장() {
            NotificationSetting setting = NotificationSetting.builder()
                    .user(user)
                    .positionEntryEnabled(true)
                    .positionExitEnabled(true)
                    .build();
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.of(setting));
            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(notificationRepository.save(any())).willReturn(notification);

            notificationService.createPositionNotification(
                    user.getId(), null, NotificationType.POSITION_ENTRY, "포지션 오픈", "메시지");

            verify(notificationRepository).save(any());
        }

        @Test
        @DisplayName("포지션 진입 알림이 비활성이면 저장하지 않는다")
        void 포지션_진입_알림_비활성이면_저장_안함() {
            NotificationSetting setting = NotificationSetting.builder()
                    .user(user)
                    .positionEntryEnabled(false)
                    .positionExitEnabled(true)
                    .build();
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.of(setting));

            notificationService.createPositionNotification(
                    user.getId(), null, NotificationType.POSITION_ENTRY, "포지션 오픈", "메시지");

            verify(notificationRepository, never()).save(any());
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("포지션 청산 알림이 비활성이면 저장하지 않는다")
        void 포지션_청산_알림_비활성이면_저장_안함() {
            NotificationSetting setting = NotificationSetting.builder()
                    .user(user)
                    .positionEntryEnabled(true)
                    .positionExitEnabled(false)
                    .build();
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.of(setting));

            notificationService.createPositionNotification(
                    user.getId(), null, NotificationType.POSITION_EXIT, "포지션 청산", "메시지");

            verify(notificationRepository, never()).save(any());
            verify(userRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("getSetting")
    class GetSetting {

        @Test
        @DisplayName("설정이 있으면 저장된 값을 반환한다")
        void 설정_있으면_저장된_값_반환() {
            NotificationSetting setting = NotificationSetting.builder()
                    .user(user)
                    .positionEntryEnabled(true)
                    .positionExitEnabled(false)
                    .build();
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.of(setting));

            NotificationSettingResponse result = notificationService.getSetting(user.getId());

            assertThat(result.isPositionEntryEnabled()).isTrue();
            assertThat(result.isPositionExitEnabled()).isFalse();
        }

        @Test
        @DisplayName("설정이 없으면 기본값(모두 활성)을 반환한다")
        void 설정_없으면_기본값_반환() {
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.empty());

            NotificationSettingResponse result = notificationService.getSetting(user.getId());

            assertThat(result.isPositionEntryEnabled()).isTrue();
            assertThat(result.isPositionExitEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateSetting")
    class UpdateSetting {

        @Test
        @DisplayName("설정이 없으면 새로 생성한다")
        void 설정_없으면_새로_생성() {
            NotificationSettingRequest request = mock(NotificationSettingRequest.class);
            given(request.getPositionEntryEnabled()).willReturn(false);
            given(request.getPositionExitEnabled()).willReturn(true);

            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.empty());
            given(notificationSettingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            NotificationSettingResponse result = notificationService.updateSetting(user.getId(), request);

            assertThat(result.isPositionEntryEnabled()).isFalse();
            assertThat(result.isPositionExitEnabled()).isTrue();
            verify(notificationSettingRepository).save(any());
        }

        @Test
        @DisplayName("설정이 있으면 기존 값을 업데이트한다")
        void 설정_있으면_기존_값_업데이트() {
            NotificationSetting existing = NotificationSetting.builder()
                    .user(user)
                    .positionEntryEnabled(true)
                    .positionExitEnabled(true)
                    .build();
            NotificationSettingRequest request = mock(NotificationSettingRequest.class);
            given(request.getPositionEntryEnabled()).willReturn(false);
            given(request.getPositionExitEnabled()).willReturn(false);

            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(notificationSettingRepository.findByUserId(user.getId())).willReturn(Optional.of(existing));
            given(notificationSettingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            NotificationSettingResponse result = notificationService.updateSetting(user.getId(), request);

            assertThat(result.isPositionEntryEnabled()).isFalse();
            assertThat(result.isPositionExitEnabled()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 유저면 예외 발생")
        void 존재하지_않는_유저면_예외_발생() {
            NotificationSettingRequest request = mock(NotificationSettingRequest.class);
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.updateSetting(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

            verify(notificationSettingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getNotifications")
    class GetNotifications {

        @Test
        @DisplayName("사용자 알림 목록 조회 성공")
        void 사용자_알림_목록_조회_성공() {
            given(notificationRepository.findAllByUserId(user.getId())).willReturn(List.of(notification));

            List<NotificationResponse> result = notificationService.getNotifications(user.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(notification.getId());
            assertThat(result.get(0).getType()).isEqualTo(notification.getType());
        }

        @Test
        @DisplayName("알림이 없으면 빈 리스트 반환")
        void 알림이_없으면_빈_리스트_반환() {
            given(notificationRepository.findAllByUserId(user.getId())).willReturn(List.of());

            List<NotificationResponse> result = notificationService.getNotifications(user.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("본인 알림 읽음 처리 성공")
        void 본인_알림_읽음_처리_성공() {
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            NotificationResponse result = notificationService.markAsRead(notification.getId(), user.getId());

            assertThat(result.isRead()).isTrue();
        }

        @Test
        @DisplayName("다른 사용자의 알림 읽음 처리 시 접근 거부")
        void 다른_사용자의_알림_읽음_처리_시_접근_거부() {
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationService.markAsRead(notification.getId(), otherUser.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        @Test
        @DisplayName("존재하지 않는 알림 읽음 처리 시 예외 발생")
        void 존재하지_않는_알림_읽음_처리_시_예외_발생() {
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(999L, user.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("deleteNotification")
    class DeleteNotification {

        @Test
        @DisplayName("본인 알림 삭제 성공")
        void 본인_알림_삭제_성공() {
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            notificationService.deleteNotification(notification.getId(), user.getId());

            verify(notificationRepository).delete(notification);
        }

        @Test
        @DisplayName("다른 사용자의 알림 삭제 시 접근 거부")
        void 다른_사용자의_알림_삭제_시_접근_거부() {
            given(notificationRepository.findById(notification.getId())).willReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationService.deleteNotification(notification.getId(), otherUser.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_ACCESS_DENIED);

            verify(notificationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("존재하지 않는 알림 삭제 시 예외 발생")
        void 존재하지_않는_알림_삭제_시_예외_발생() {
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.deleteNotification(999L, user.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);

            verify(notificationRepository, never()).delete(any());
        }
    }
}
