package com.example.htmldeploy.infrastructure.storage.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;

import com.example.htmldeploy.domain.artifact.model.ArtifactManifest;
import com.example.htmldeploy.domain.artifact.port.ArchiveInspection;
import com.example.htmldeploy.domain.artifact.port.ArchiveInspector;
import com.example.htmldeploy.domain.artifact.service.ArchivePolicy;
import com.example.htmldeploy.domain.shared.DomainException;

@Component
public class LocalArchiveInspector implements ArchiveInspector {

    @Override
    public ArchiveInspection inspectAndExtract(Path zipFile, Path destination, ArchivePolicy policy) {
        try {
            LocalPaths.deleteRecursively(destination);
            Files.createDirectories(destination);
            if (Files.size(zipFile) > policy.maxZipBytes()) {
                throw new DomainException("artifact.too_large", "ZIP file exceeds configured limit");
            }
            List<String> files = new ArrayList<>();
            Set<String> normalizedNames = new HashSet<>();
            long totalBytes = 0;
            try (ZipFile archive = ZipFile.builder().setPath(zipFile).get()) {
                Enumeration<ZipArchiveEntry> entries = archive.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (entry.isUnixSymlink()) {
                        throw new DomainException("artifact.symlink", "symbolic links are not allowed");
                    }
                    String name = normalizeEntryName(entry.getName());
                    if (name.isBlank() || name.length() > policy.maxPathLength()) {
                        throw new DomainException("artifact.invalid_path", "archive contains an invalid path");
                    }
                    String collisionKey = name.toLowerCase(Locale.ROOT);
                    if (!normalizedNames.add(collisionKey)) {
                        throw new DomainException("artifact.duplicate_path", "archive contains duplicate paths");
                    }
                    Path target = destination.resolve(name).normalize();
                    if (!target.startsWith(destination)) {
                        throw new DomainException("artifact.path_traversal", "archive entry escapes destination");
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream input = archive.getInputStream(entry)) {
                        totalBytes = copyWithLimit(input, target, totalBytes, policy.maxExpandedBytes());
                    }
                    files.add(name);
                    if (files.size() > policy.maxFiles()) {
                        throw new DomainException("artifact.too_many_files", "archive contains too many files");
                    }
                }
            }
            files.sort(String::compareTo);
            return new ArchiveInspection(new ArtifactManifest(
                    files.size(),
                    totalBytes,
                    policy.requiredEntryPoint(),
                    files
            ));
        } catch (DomainException exception) {
            cleanupAfterFailure(destination);
            throw exception;
        } catch (IOException exception) {
            cleanupAfterFailure(destination);
            throw new DomainException("artifact.invalid_archive", "failed to read ZIP archive");
        }
    }

    private long copyWithLimit(InputStream input, Path target, long currentTotal, long maxBytes) throws IOException {
        long total = currentTotal;
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new DomainException("artifact.expanded_too_large", "expanded archive exceeds limit");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private String normalizeEntryName(String rawName) {
        String normalized = rawName.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")) {
            throw new DomainException("artifact.path_traversal", "archive entry escapes destination");
        }
        return normalized;
    }

    private void cleanupAfterFailure(Path destination) {
        try {
            LocalPaths.deleteRecursively(destination);
        } catch (IOException ignored) {
            // The original error is more useful than cleanup failure.
        }
    }
}
