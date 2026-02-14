package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitgetPositionMessage {
    private String action;    // "snapshot" / "update"
    private ArgInfo arg;
    private List<BitgetPositionData> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArgInfo {
        private String instType;
        private String channel;
        private String instId;
    }
}