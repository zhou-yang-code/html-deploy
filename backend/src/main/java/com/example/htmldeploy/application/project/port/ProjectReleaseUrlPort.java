package com.example.htmldeploy.application.project.port;

import java.util.Optional;

import com.example.htmldeploy.domain.project.model.ProjectId;

public interface ProjectReleaseUrlPort {

    Optional<String> activeReleaseUrl(ProjectId projectId);
}
