package hello.tradexserver.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChartLayoutRequest {

    @NotBlank
    private String name;

    private String symbol;

    private String resolution;

    @NotBlank
    private String content;
}
