package com.example.htmldeploy.infrastructure.storage.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Component;

import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.artifact.port.ArtifactContentStore;
import com.example.htmldeploy.infrastructure.config.AppProperties;

@Component
public class LocalArtifactContentStore implements ArtifactContentStore {

    private final Path contentRoot;

    public LocalArtifactContentStore(AppProperties properties) {
        this.contentRoot = properties.storageRoot().resolve("content").toAbsolutePath().normalize();
    }

    @Override
    public void storeExtracted(ArtifactId artifactId, Path sourceDirectory) {
        Path target = contentPath(artifactId);
        try {
            Files.createDirectories(target.getParent());
            LocalPaths.deleteRecursively(target);
            try {
                Files.move(sourceDirectory, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                LocalPaths.copyRecursively(sourceDirectory, target);
                LocalPaths.deleteRecursively(sourceDirectory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to store extracted artifact", exception);
        }
    }

    @Override
    public Path contentPath(ArtifactId artifactId) {
        return contentRoot.resolve(artifactId.value().toString());
    }
}
