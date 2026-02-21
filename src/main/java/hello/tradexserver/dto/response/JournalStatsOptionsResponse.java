package hello.tradexserver.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class JournalStatsOptionsResponse {

    private List<String> indicators;
    private List<String> timeframes;
    private List<String> technicalAnalyses;
}
