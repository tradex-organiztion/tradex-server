package hello.tradexserver.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class BybitClosedPnlResponse {
    private Integer retCode;
    private String retMsg;
    private BybitClosedPnlData result;
}
