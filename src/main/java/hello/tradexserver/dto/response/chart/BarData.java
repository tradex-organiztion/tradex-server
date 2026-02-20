package hello.tradexserver.dto.response.chart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BarData {
    private long time;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}
