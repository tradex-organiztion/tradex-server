package hello.tradexserver.openApi.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitExecutionData {
    private String nextPageCursor;
    private String category;
    private List<BybitExecution> list;
}
