package com.example.htmldeploy.infrastructure.storage.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.artifact.port.ArtifactObject;
import com.example.htmldeploy.domain.artifact.port.ArtifactStorage;
import com.example.htmldeploy.domain.artifact.port.UploadTarget;
import com.example.htmldeploy.infrastructure.config.AppProperties;
import com.example.htmldeploy.infrastructure.storage.UploadTokenService;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalArtifactStorage implements ArtifactStorage {

    private final Path artifactRoot;
    private final AppProperties properties;
    private final UploadTokenService uploadTokens;

    public LocalArtifactStorage(
            AppProperties properties,
            UploadTokenService uploadTokens
    ) {
        this.properties = properties;
        this.uploadTokens = uploadTokens;
        this.artifactRoot = properties.storageRoot().resolve("artifacts").toAbsolutePath().normalize();
    }

    @Override
    public UploadTarget createUploadTarget(Artifact artifact, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        String token = uploadTokens.issue(artifact.id().value(), expiresAt);
        String url = properties.publicBaseUrl()
                + "/api/v1/artifacts/" + artifact.id().value()
                + "/content?token=" + token;
        return new UploadTarget("PUT", url, expiresAt, java.util.Map.of("Content-Type", "application/zip"));
    }

    @Override
    public void store(String objectKey, InputStream inputStream, long maxBytes) {
        Path target = LocalPaths.resolveInside(artifactRoot, objectKey);
        Path temp = target.resolveSibling(target.getFileName() + ".uploading");
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(temp);
            long total = 0;
            try (OutputStream output = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IllegalArgumentException("upload exceeds configured size limit");
                    }
                    output.write(buffer, 0, read);
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to store uploaded artifact", exception);
        }
    }

    @Override
    public ArtifactObject stat(String objectKey) {
        Path target = LocalPaths.resolveInside(artifactRoot, objectKey);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("artifact object does not exist");
        }
        try {
            return new ArtifactObject(objectKey, Files.size(target), "application/zip");
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read artifact metadata", exception);
        }
    }

    @Override
    public Path downloadToTemp(String objectKey, Path tempDirectory) {
        Path source = LocalPaths.resolveInside(artifactRoot, objectKey);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("artifact object does not exist");
        }
        try {
            Files.createDirectories(tempDirectory);
            Path target = tempDirectory.resolve("artifact.zip");
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to download artifact", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(LocalPaths.resolveInside(artifactRoot, objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to delete artifact", exception);
        }
    }
}
