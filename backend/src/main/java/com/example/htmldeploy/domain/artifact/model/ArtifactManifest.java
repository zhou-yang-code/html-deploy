package com.example.htmldeploy.domain.artifact.model;

import java.util.List;

public record ArtifactManifest(
        int fileCount,
        long totalBytes,
        String entryPoint,
        List<String> files
) {
    public ArtifactManifest {
        files = files == null ? List.of() : List.copyOf(files);
        if (fileCount < 0 || totalBytes < 0) {
            throw new IllegalArgumentException("manifest counters must not be negative");
        }
    }
}
