package hello.tradexserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final AwsS3Properties props;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (StringUtils.hasText(props.getAccessKey()) && StringUtils.hasText(props.getSecretKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }
}