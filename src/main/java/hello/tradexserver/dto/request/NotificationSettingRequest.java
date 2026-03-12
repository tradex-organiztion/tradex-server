package hello.tradexserver.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationSettingRequest {

    @NotNull
    private Boolean positionEntryEnabled;

    @NotNull
    private Boolean positionExitEnabled;
}
