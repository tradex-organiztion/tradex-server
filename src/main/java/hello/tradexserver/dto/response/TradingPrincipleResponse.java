package hello.tradexserver.dto.response;

import hello.tradexserver.domain.TradingPrinciple;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TradingPrincipleResponse {

    private Long id;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TradingPrincipleResponse from(TradingPrinciple principle) {
        return TradingPrincipleResponse.builder()
                .id(principle.getId())
                .content(principle.getContent())
                .createdAt(principle.getCreatedAt())
                .updatedAt(principle.getUpdatedAt())
                .build();
    }
}
