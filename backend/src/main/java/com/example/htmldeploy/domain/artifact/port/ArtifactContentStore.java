package com.example.htmldeploy.domain.artifact.port;

import java.nio.file.Path;

import com.example.htmldeploy.domain.artifact.model.ArtifactId;

public interface ArtifactContentStore {

    void storeExtracted(ArtifactId artifactId, Path sourceDirectory);

    Path contentPath(ArtifactId artifactId);
}
