package hello.tradexserver.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PrincipleCheckResponse {

    private Long tradingPrincipleId;
    private String content;
    private boolean isChecked;

    public static PrincipleCheckResponse of(Long tradingPrincipleId, String content, boolean isChecked) {
        return PrincipleCheckResponse.builder()
                .tradingPrincipleId(tradingPrincipleId)
                .content(content)
                .isChecked(isChecked)
                .build();
    }
}