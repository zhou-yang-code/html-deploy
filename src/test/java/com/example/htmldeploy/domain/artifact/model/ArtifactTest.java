package com.example.htmldeploy.domain.artifact.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.shared.DomainException;

class ArtifactTest {

    @Test
    void validatesArtifactLifecycle() {
        Artifact artifact = Artifact.create(new ProjectId(UUID.randomUUID()), "site.zip", "objects/site.zip");

        artifact.markUploaded(1024);
        artifact.startValidation();
        artifact.markValidated(
                new Sha256("a".repeat(64)),
                new ArtifactManifest(2, 2048, "index.html", List.of("index.html", "style.css"))
        );

        assertThat(artifact.status()).isEqualTo(ArtifactStatus.READY);
        assertThat(artifact.sha256().value()).isEqualTo("a".repeat(64));
        assertThat(artifact.manifest().fileCount()).isEqualTo(2);
    }

    @Test
    void rejectsValidationFromCreatedState() {
        Artifact artifact = Artifact.create(new ProjectId(UUID.randomUUID()), "site.zip", "objects/site.zip");

        assertThatThrownBy(artifact::startValidation)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not ready for validation");
    }
}
