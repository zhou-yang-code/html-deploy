package com.example.htmldeploy.domain.artifact.port;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

import com.example.htmldeploy.domain.artifact.model.Artifact;

public interface ArtifactStorage {

    UploadTarget createUploadTarget(Artifact artifact, Duration ttl);

    void store(String objectKey, InputStream inputStream, long maxBytes);

    ArtifactObject stat(String objectKey);

    Path downloadToTemp(String objectKey, Path tempDirectory);

    void delete(String objectKey);
}
