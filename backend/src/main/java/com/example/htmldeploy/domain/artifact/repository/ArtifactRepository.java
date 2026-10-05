package com.example.htmldeploy.domain.artifact.repository;

import java.util.Optional;

import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.artifact.model.Sha256;
import com.example.htmldeploy.domain.project.model.ProjectId;

public interface ArtifactRepository {

    Artifact save(Artifact artifact);

    Optional<Artifact> findById(ArtifactId id);

    Optional<Artifact> findByProjectIdAndSha256(ProjectId projectId, Sha256 sha256);
}
