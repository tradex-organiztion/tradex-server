package hello.tradexserver.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TradingPrincipleRequest {

    @NotBlank(message = "매매 원칙 내용은 필수입니다")
    private String content;
}
