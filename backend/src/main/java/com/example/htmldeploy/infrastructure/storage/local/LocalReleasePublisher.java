package com.example.htmldeploy.infrastructure.storage.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Component;

import com.example.htmldeploy.domain.deployment.port.ReleasePublishRequest;
import com.example.htmldeploy.domain.deployment.port.ReleasePublisher;
import com.example.htmldeploy.infrastructure.config.AppProperties;

@Component
public class LocalReleasePublisher implements ReleasePublisher {

    private final Path releasesRoot;
    private final Path nginxRoot;

    public LocalReleasePublisher(AppProperties properties) {
        this.releasesRoot = properties.storageRoot().resolve("releases").toAbsolutePath().normalize();
        this.nginxRoot = properties.nginxRoot().toAbsolutePath().normalize();
    }

    @Override
    public String publish(ReleasePublishRequest request) {
        Path releaseDirectory = releasesRoot
                .resolve(request.projectId().toString())
                .resolve(request.deploymentId().toString())
                .normalize();
        try {
            LocalPaths.deleteRecursively(releaseDirectory);
            Files.createDirectories(releaseDirectory.getParent());
            LocalPaths.copyRecursively(request.artifactContentDirectory(), releaseDirectory);
            updateCurrentLink(request, releaseDirectory);
            return releaseDirectory.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to publish release", exception);
        }
    }

    private void updateCurrentLink(ReleasePublishRequest request, Path releaseDirectory) throws IOException {
        String siteName = request.tenantSlug() + "-" + request.projectSlug();
        Path siteDirectory = nginxRoot.resolve(siteName);
        Path current = siteDirectory.resolve("current");
        Files.createDirectories(siteDirectory);
        if (Files.isSymbolicLink(current)) {
            Files.deleteIfExists(current);
        } else if (Files.exists(current)) {
            LocalPaths.deleteRecursively(current);
        }
        Path tempLink = siteDirectory.resolve(".current-" + request.deploymentId());
        Files.deleteIfExists(tempLink);
        try {
            Files.createSymbolicLink(tempLink, siteDirectory.relativize(releaseDirectory));
            Files.move(tempLink, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | UnsupportedOperationException symlinkFailure) {
            Files.deleteIfExists(tempLink);
            LocalPaths.copyRecursively(releaseDirectory, current);
            Files.writeString(siteDirectory.resolve("release-path.txt"), releaseDirectory.toString());
        }
    }
}
