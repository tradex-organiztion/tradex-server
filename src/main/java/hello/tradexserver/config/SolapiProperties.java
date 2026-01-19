package hello.tradexserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "solapi")
@Getter
@Setter
public class SolapiProperties {
    private String apiKey;
    private String apiSecret;
    private String senderNumber;
}
