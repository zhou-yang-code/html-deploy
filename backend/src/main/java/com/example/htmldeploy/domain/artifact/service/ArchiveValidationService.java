package com.example.htmldeploy.domain.artifact.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.example.htmldeploy.domain.artifact.model.ArtifactManifest;
import com.example.htmldeploy.domain.shared.DomainException;

public final class ArchiveValidationService {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".com", ".msi", ".ps1", ".sh"
    );

    public void validate(ArtifactManifest manifest, ArchivePolicy policy) {
        if (manifest.fileCount() > policy.maxFiles()) {
            throw new DomainException("artifact.too_many_files", "archive contains too many files");
        }
        if (manifest.totalBytes() > policy.maxExpandedBytes()) {
            throw new DomainException("artifact.expanded_too_large", "expanded archive is too large");
        }
        if (!manifest.files().contains(policy.requiredEntryPoint())) {
            throw new DomainException(
                    "artifact.missing_entrypoint",
                    "archive must contain " + policy.requiredEntryPoint() + " at its root"
            );
        }
        List<String> blocked = manifest.files().stream()
                .filter(this::hasBlockedExtension)
                .limit(10)
                .toList();
        if (!blocked.isEmpty()) {
            throw new DomainException("artifact.blocked_file_type", "archive contains blocked files: " + blocked);
        }
    }

    private boolean hasBlockedExtension(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return BLOCKED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}
