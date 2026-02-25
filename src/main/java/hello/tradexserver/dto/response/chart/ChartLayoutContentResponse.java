package hello.tradexserver.dto.response.chart;

import hello.tradexserver.domain.ChartLayout;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChartLayoutContentResponse {

    private Long id;
    private String content;

    public static ChartLayoutContentResponse from(ChartLayout layout) {
        return ChartLayoutContentResponse.builder()
                .id(layout.getId())
                .content(layout.getContent())
                .build();
    }
}
