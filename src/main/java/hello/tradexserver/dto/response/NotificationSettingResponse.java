package hello.tradexserver.dto.response;

import hello.tradexserver.domain.NotificationSetting;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSettingResponse {

    private boolean positionEntryEnabled;
    private boolean positionExitEnabled;

    public static NotificationSettingResponse from(NotificationSetting setting) {
        return NotificationSettingResponse.builder()
                .positionEntryEnabled(setting.isPositionEntryEnabled())
                .positionExitEnabled(setting.isPositionExitEnabled())
                .build();
    }

    public static NotificationSettingResponse allEnabled() {
        return NotificationSettingResponse.builder()
                .positionEntryEnabled(true)
                .positionExitEnabled(true)
                .build();
    }
}
