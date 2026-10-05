package com.example.htmldeploy.infrastructure.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageConfiguration {

    @Bean
    S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(true);
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        if (StringUtils.hasText(properties.accessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
            ));
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()));
        String presignEndpoint = StringUtils.hasText(properties.publicEndpoint())
                ? properties.publicEndpoint()
                : properties.endpoint();
        if (StringUtils.hasText(presignEndpoint)) {
            builder.endpointOverride(URI.create(presignEndpoint));
        }
        if (StringUtils.hasText(properties.accessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
            ));
        }
        return builder.build();
    }
}
