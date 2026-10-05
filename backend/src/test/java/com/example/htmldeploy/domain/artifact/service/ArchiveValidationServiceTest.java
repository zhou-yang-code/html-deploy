package com.example.htmldeploy.domain.artifact.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.htmldeploy.domain.artifact.model.ArtifactManifest;
import com.example.htmldeploy.domain.shared.DomainException;

class ArchiveValidationServiceTest {

    private final ArchiveValidationService service = new ArchiveValidationService();
    private final ArchivePolicy policy = new ArchivePolicy(1024, 4096, 20, 200, "index.html");

    @Test
    void acceptsSafeStaticSite() {
        service.validate(
                new ArtifactManifest(3, 2048, "index.html", List.of("index.html", "style.css", "app.js")),
                policy
        );
    }

    @Test
    void rejectsMissingEntryPoint() {
        assertThatThrownBy(() -> service.validate(
                new ArtifactManifest(1, 100, "index.html", List.of("home.html")),
                policy
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("index.html")
                .hasMessageContaining("home.html");
    }

    @Test
    void rejectsExecutableFiles() {
        assertThatThrownBy(() -> service.validate(
                new ArtifactManifest(2, 200, "index.html", List.of("index.html", "payload.exe")),
                policy
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("blocked files");
    }
}
