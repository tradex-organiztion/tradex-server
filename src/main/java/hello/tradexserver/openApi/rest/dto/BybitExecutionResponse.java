package hello.tradexserver.openApi.rest.dto;

import lombok.Data;

@Data
public class BybitExecutionResponse {
    private Integer retCode;
    private String retMsg;
    private BybitExecutionData result;
}
