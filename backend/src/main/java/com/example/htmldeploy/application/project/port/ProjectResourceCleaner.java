package com.example.htmldeploy.application.project.port;

import java.util.UUID;

public interface ProjectResourceCleaner {

    void deleteExternalResources(String tenantSlug, String projectSlug, UUID projectId);
}
