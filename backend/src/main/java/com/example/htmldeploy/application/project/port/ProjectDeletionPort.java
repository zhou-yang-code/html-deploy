package com.example.htmldeploy.application.project.port;

import com.example.htmldeploy.domain.project.model.ProjectId;

public interface ProjectDeletionPort {

    void deletePlatformData(ProjectId projectId);
}
