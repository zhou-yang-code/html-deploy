package com.example.htmldeploy.infrastructure.storage.s3;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.artifact.port.ArtifactObject;
import com.example.htmldeploy.domain.artifact.port.ArtifactStorage;
import com.example.htmldeploy.domain.artifact.port.UploadTarget;
import com.example.htmldeploy.infrastructure.config.StorageProperties;

import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ArtifactStorage implements ArtifactStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3ArtifactStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public UploadTarget createUploadTarget(Artifact artifact, Duration ttl) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(artifact.objectKey())
                .contentType("application/zip")
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putRequest)
                .build();
        var presigned = presigner.presignPutObject(presignRequest);
        return new UploadTarget(
                presigned.httpRequest().method().name(),
                presigned.url().toString(),
                java.time.Instant.now().plus(ttl),
                presigned.signedHeaders().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                java.util.Map.Entry::getKey,
                                entry -> String.join(",", entry.getValue())
                        ))
        );
    }

    @Override
    public void store(String objectKey, InputStream inputStream, long maxBytes) {
        throw new UnsupportedOperationException("direct upload is handled by the S3 presigned URL");
    }

    @Override
    public ArtifactObject stat(String objectKey) {
        HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
        return new ArtifactObject(objectKey, response.contentLength(), response.contentType());
    }

    @Override
    public Path downloadToTemp(String objectKey, Path tempDirectory) {
        try {
            java.nio.file.Files.createDirectories(tempDirectory);
            Path target = tempDirectory.resolve("artifact.zip");
            s3.getObject(
                    GetObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build(),
                    ResponseTransformer.toFile(target)
            );
            return target;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("failed to create artifact temp directory", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
    }
}
