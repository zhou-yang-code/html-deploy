package com.example.htmldeploy.domain.deployment.repository;

import java.util.Optional;

import com.example.htmldeploy.domain.deployment.model.ReleaseChannel;
import com.example.htmldeploy.domain.project.model.ProjectId;

public interface ReleaseChannelRepository {

    ReleaseChannel save(ReleaseChannel channel);

    Optional<ReleaseChannel> findByProjectIdAndEnvironment(ProjectId projectId, String environment);
}
