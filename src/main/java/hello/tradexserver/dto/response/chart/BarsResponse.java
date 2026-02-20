package hello.tradexserver.dto.response.chart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BarsResponse {
    private List<BarData> bars;
    private boolean noData;
}
