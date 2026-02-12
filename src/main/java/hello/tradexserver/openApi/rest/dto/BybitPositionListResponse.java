package hello.tradexserver.openApi.rest.dto;

import lombok.Data;

@Data
public class BybitPositionListResponse {
    private Integer retCode;
    private String retMsg;
    private BybitPositionListData result;
}