package hello.tradexserver.dto.response.chart;

import hello.tradexserver.domain.ChartLayout;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChartLayoutMetaResponse {

    private Long id;
    private String name;
    private String symbol;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ChartLayoutMetaResponse from(ChartLayout layout) {
        return ChartLayoutMetaResponse.builder()
                .id(layout.getId())
                .name(layout.getName())
                .symbol(layout.getSymbol())
                .resolution(layout.getResolution())
                .createdAt(layout.getCreatedAt())
                .updatedAt(layout.getUpdatedAt())
                .build();
    }
}
