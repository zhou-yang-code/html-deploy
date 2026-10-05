package com.example.htmldeploy.domain.deployment.repository;

import java.util.List;
import java.util.Optional;

import com.example.htmldeploy.domain.deployment.model.Deployment;
import com.example.htmldeploy.domain.deployment.model.DeploymentId;
import com.example.htmldeploy.domain.deployment.model.DeploymentStatus;
import com.example.htmldeploy.domain.project.model.ProjectId;

public interface DeploymentRepository {

    Deployment save(Deployment deployment);

    Optional<Deployment> findById(DeploymentId id);

    List<Deployment> findByProjectId(ProjectId projectId);

    Optional<Deployment> findActive(ProjectId projectId, String environment);

    long nextVersion(ProjectId projectId);

    List<Deployment> findByProjectIdAndStatus(ProjectId projectId, DeploymentStatus status);
}
