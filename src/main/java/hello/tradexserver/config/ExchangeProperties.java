package hello.tradexserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "exchange")
public class ExchangeProperties {

    private BinanceConfig binance = new BinanceConfig();
    private BybitConfig bybit = new BybitConfig();
    private BitgetConfig bitget = new BitgetConfig();

    @Getter
    @Setter
    public static class BinanceConfig {
        private String restUrl;
        private String wsUrl;
    }

    @Getter
    @Setter
    public static class BybitConfig {
        private String restUrl;
        private String wsUrl;
    }

    @Getter
    @Setter
    public static class BitgetConfig {
        private String restUrl;
        private String wsUrl;
        private boolean demoMode;
    }
}
