package com.example.htmldeploy.infrastructure.storage.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private static final String MACOS_METADATA_DIRECTORY = "__MACOSX/";
    private static final String DS_STORE = ".DS_Store";

    @Override
    public ArchiveInspection inspectAndExtract(Path zipFile, Path destination, ArchivePolicy policy) {
        try {
            LocalPaths.deleteRecursively(destination);
            Files.createDirectories(destination);
            if (Files.size(zipFile) > policy.maxZipBytes()) {
                throw new DomainException("artifact.too_large", "ZIP file exceeds configured limit");
            }
            try (ZipFile archive = ZipFile.builder().setPath(zipFile).get()) {
                List<Entry> entries = readEntries(archive, policy);
                String stripPrefix = detectWrapperDirectory(entries, policy.requiredEntryPoint());
                return extract(archive, entries, destination, stripPrefix, policy);
            }
        } catch (DomainException exception) {
            cleanupAfterFailure(destination);
            throw exception;
        } catch (IOException exception) {
            cleanupAfterFailure(destination);
            throw new DomainException("artifact.invalid_archive", "failed to read ZIP archive");
        }
    }

    private List<Entry> readEntries(ZipFile archive, ArchivePolicy policy) {
        List<Entry> entries = new ArrayList<>();
        Set<String> normalizedNames = new HashSet<>();
        Enumeration<ZipArchiveEntry> rawEntries = archive.getEntries();
        while (rawEntries.hasMoreElements()) {
            ZipArchiveEntry entry = rawEntries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (entry.isUnixSymlink()) {
                throw new DomainException("artifact.symlink", "symbolic links are not allowed");
            }
            String name = normalizeEntryName(entry.getName());
            if (isArchiveMetadata(name)) {
                continue;
            }
            if (name.isBlank() || name.length() > policy.maxPathLength()) {
                throw new DomainException("artifact.invalid_path", "archive contains an invalid path");
            }
            if (!normalizedNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new DomainException("artifact.duplicate_path", "archive contains duplicate paths");
            }
            entries.add(new Entry(entry, name));
        }
        return entries;
    }

    private ArchiveInspection extract(
            ZipFile archive,
            List<Entry> entries,
            Path destination,
            String stripPrefix,
            ArchivePolicy policy
    ) throws IOException {
        List<String> files = new ArrayList<>();
        long totalBytes = 0;
        for (Entry entry : entries) {
            String name = stripPrefix.isEmpty() ? entry.name() : entry.name().substring(stripPrefix.length());
            if (name.isBlank()) {
                continue;
            }
            Path target = destination.resolve(name).normalize();
            if (!target.startsWith(destination)) {
                throw new DomainException("artifact.path_traversal", "archive entry escapes destination");
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = archive.getInputStream(entry.entry())) {
                totalBytes = copyWithLimit(input, target, totalBytes, policy.maxExpandedBytes());
            }
            files.add(name);
            if (files.size() > policy.maxFiles()) {
                throw new DomainException("artifact.too_many_files", "archive contains too many files");
            }
        }
        files.sort(String::compareTo);
        return new ArchiveInspection(new ArtifactManifest(
                files.size(),
                totalBytes,
                policy.requiredEntryPoint(),
                files
        ));
    }

    /**
     * Users commonly zip the folder that contains index.html instead of its contents.
     * When every entry lives under one directory and that directory holds the entry point,
     * drop the wrapper so the archive behaves like a normal site bundle.
     */
    private String detectWrapperDirectory(List<Entry> entries, String entryPoint) {
        Set<String> names = new LinkedHashSet<>();
        for (Entry entry : entries) {
            names.add(entry.name());
        }
        if (names.contains(entryPoint)) {
            return "";
        }
        Set<String> roots = new LinkedHashSet<>();
        for (String name : names) {
            int slash = name.indexOf('/');
            roots.add(slash < 0 ? "" : name.substring(0, slash));
        }
        if (roots.size() != 1) {
            return "";
        }
        String root = roots.iterator().next();
        if (root.isEmpty()) {
            return "";
        }
        return names.contains(root + "/" + entryPoint) ? root + "/" : "";
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

    private boolean isArchiveMetadata(String name) {
        if (name.startsWith(MACOS_METADATA_DIRECTORY)) {
            return true;
        }
        int slash = name.lastIndexOf('/');
        String fileName = slash < 0 ? name : name.substring(slash + 1);
        return DS_STORE.equals(fileName) || fileName.startsWith("._");
    }

    private void cleanupAfterFailure(Path destination) {
        try {
            LocalPaths.deleteRecursively(destination);
        } catch (IOException ignored) {
            // The original error is more useful than cleanup failure.
        }
    }

    private record Entry(ZipArchiveEntry entry, String name) {
    }
}
