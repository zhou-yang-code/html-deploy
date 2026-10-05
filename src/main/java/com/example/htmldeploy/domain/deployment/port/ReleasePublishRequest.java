package com.example.htmldeploy.domain.deployment.port;

import java.nio.file.Path;
import java.util.UUID;

public record ReleasePublishRequest(
        UUID tenantId,
        UUID projectId,
        UUID deploymentId,
        UUID artifactId,
        String tenantSlug,
        String projectSlug,
        String environment,
        Path artifactContentDirectory
) {
}
