package com.example.htmldeploy.domain.artifact.service;

public record ArchivePolicy(
        long maxZipBytes,
        long maxExpandedBytes,
        int maxFiles,
        int maxPathLength,
        String requiredEntryPoint
) {
}
