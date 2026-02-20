package hello.tradexserver.dto.response.chart;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SymbolInfoResponse {
    private String name;
    private String ticker;
    private String description;
    private String type;
    private String session;
    private String exchange;
    private String timezone;
    private int pricescale;
    private int minmov;

    @JsonProperty("has_intraday")
    private boolean hasIntraday;

    @JsonProperty("has_weekly_and_monthly")
    private boolean hasWeeklyAndMonthly;

    @JsonProperty("supported_resolutions")
    private List<String> supportedResolutions;

    @JsonProperty("volume_precision")
    private int volumePrecision;
}
