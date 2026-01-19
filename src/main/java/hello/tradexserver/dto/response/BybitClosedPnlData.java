package hello.tradexserver.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class BybitClosedPnlData {
    private String nextPageCursor;
    private String category;
    private List<BybitClosedPnl> list;
}
