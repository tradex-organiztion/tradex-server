package hello.tradexserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "exchange")
public class ExchangeProperties {

    private ExchangeConfig bybit = new ExchangeConfig();
    private ExchangeConfig binance = new ExchangeConfig();
    private BitgetConfig bitget = new BitgetConfig();

    @Getter
    @Setter
    public static class ExchangeConfig {
        private String restBaseUrl;
        private String wsBaseUrl;
    }

    @Getter
    @Setter
    public static class BitgetConfig extends ExchangeConfig {
        private boolean demoMode = false;
    }
}
