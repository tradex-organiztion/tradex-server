package hello.tradexserver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PrincipleCheckRequest {

    private Long tradingPrincipleId;

    @JsonProperty("isChecked")
    private boolean isChecked;
}