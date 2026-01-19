package hello.tradexserver.openApi.webSocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitPositionMessage {
    private String id;
    private String topic;
    private Long creationTime;
    private List<BybitPositionData> data;
}