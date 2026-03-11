package hello.tradexserver.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "exchange")
public class ExchangeProperties {

    @Valid
    private ExchangeConfig bybit = new ExchangeConfig();
    @Valid
    private ExchangeConfig binance = new ExchangeConfig();
    @Valid
    private BitgetConfig bitget = new BitgetConfig();

    @Getter
    @Setter
    public static class ExchangeConfig {
        @NotBlank
        private String restBaseUrl;
        @NotBlank
        private String wsBaseUrl;
    }

    @Getter
    @Setter
    public static class BitgetConfig extends ExchangeConfig {
        private boolean demoMode = false;
    }
}
