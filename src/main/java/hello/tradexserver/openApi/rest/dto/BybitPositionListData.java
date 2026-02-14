package hello.tradexserver.openApi.rest.dto;

import lombok.Data;

import java.util.List;

@Data
public class BybitPositionListData {
    private String category;
    private String nextPageCursor;
    private List<BybitPositionRestItem> list;
}