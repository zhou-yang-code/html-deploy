package com.example.htmldeploy.infrastructure.storage.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.htmldeploy.domain.artifact.service.ArchivePolicy;
import com.example.htmldeploy.domain.shared.DomainException;

class LocalArchiveInspectorTest {

    private final LocalArchiveInspector inspector = new LocalArchiveInspector();
    private final ArchivePolicy policy = new ArchivePolicy(1024 * 1024, 10 * 1024 * 1024, 100, 200, "index.html");

    Path tempDirectory;

    @BeforeEach
    void createWorkingDirectory() throws IOException {
        tempDirectory = Path.of("target", "test-data", UUID.randomUUID().toString());
        Files.createDirectories(tempDirectory);
    }

    @Test
    void extractsValidStaticSite() throws IOException {
        Path zip = createZip(
                "valid.zip",
                "index.html", "<h1>Hello</h1>",
                "assets/app.js", "console.log('ok')"
        );

        var inspection = inspector.inspectAndExtract(zip, tempDirectory.resolve("output"), policy);

        assertThat(inspection.manifest().fileCount()).isEqualTo(2);
        assertThat(Files.readString(tempDirectory.resolve("output/index.html"))).contains("Hello");
    }

    @Test
    void rejectsPathTraversal() throws IOException {
        Path zip = createZip("traversal.zip", "../evil.html", "bad");

        assertThatThrownBy(() -> inspector.inspectAndExtract(zip, tempDirectory.resolve("unsafe"), policy))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("escapes destination");
    }

    private Path createZip(String filename, String... entries) throws IOException {
        Path zip = tempDirectory.resolve(filename);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int index = 0; index < entries.length; index += 2) {
                output.putNextEntry(new ZipEntry(entries[index]));
                output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return zip;
    }
}
