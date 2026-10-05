package com.example.htmldeploy.domain.deployment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.shared.DomainException;

class DeploymentTest {

    @Test
    void activatesAndSupersedesDeployment() {
        Deployment deployment = Deployment.create(
                new ProjectId(UUID.randomUUID()),
                new ArtifactId(UUID.randomUUID()),
                "production",
                1
        );

        deployment.begin();
        deployment.activate("/srv/releases/v1");
        assertThat(deployment.status()).isEqualTo(DeploymentStatus.ACTIVE);

        deployment.supersede();
        assertThat(deployment.status()).isEqualTo(DeploymentStatus.SUPERSEDED);
    }

    @Test
    void rejectsActivationBeforeDeploying() {
        Deployment deployment = Deployment.create(
                new ProjectId(UUID.randomUUID()),
                new ArtifactId(UUID.randomUUID()),
                "production",
                1
        );

        assertThatThrownBy(() -> deployment.activate("/srv/releases/v1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not deploying");
    }
}
