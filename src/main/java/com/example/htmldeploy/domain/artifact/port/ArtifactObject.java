package com.example.htmldeploy.domain.artifact.port;

public record ArtifactObject(
        String objectKey,
        long sizeBytes,
        String contentType
) {
}
