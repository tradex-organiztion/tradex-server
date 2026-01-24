package hello.tradexserver.openApi.rest.dto;

import lombok.Data;

@Data
public class BybitClosedPnlResponse {
    private Integer retCode;
    private String retMsg;
    private BybitClosedPnlData result;
}
