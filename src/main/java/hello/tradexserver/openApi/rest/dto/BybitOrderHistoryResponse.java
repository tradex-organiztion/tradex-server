package hello.tradexserver.openApi.rest.dto;

import lombok.Data;

@Data
public class BybitOrderHistoryResponse {
    private Integer retCode;
    private String retMsg;
    private BybitOrderHistoryData result;
}