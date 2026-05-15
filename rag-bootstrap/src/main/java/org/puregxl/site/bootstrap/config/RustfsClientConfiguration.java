package org.puregxl.site.bootstrap.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class RustfsClientConfiguration {

    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private final RustfsProperties rustfsProperties;

    @Bean
    public S3Client rustfsS3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(rustfsProperties.getUrl()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        rustfsProperties.getAccessKeyId(),
                        rustfsProperties.getSecretAccessKey())))
                .region(DEFAULT_REGION)
                .forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
