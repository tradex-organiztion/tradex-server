package hello.tradexserver.openApi.rest.dto;

import lombok.Data;

import java.util.List;

@Data
public class BybitClosedPnlData {
    private String nextPageCursor;
    private String category;
    private List<BybitClosedPnl> list;
}
