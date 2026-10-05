package com.example.htmldeploy.infrastructure.persistence.deployment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.domain.deployment.model.ChannelId;
import com.example.htmldeploy.domain.deployment.model.DeploymentId;
import com.example.htmldeploy.domain.deployment.model.ReleaseChannel;
import com.example.htmldeploy.domain.deployment.repository.ReleaseChannelRepository;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.infrastructure.persistence.identity.JdbcUserAccountRepository;
import com.example.htmldeploy.infrastructure.persistence.JdbcTime;

@Repository
public class JdbcReleaseChannelRepository implements ReleaseChannelRepository {

    private final JdbcClient jdbc;

    public JdbcReleaseChannelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReleaseChannel save(ReleaseChannel channel) {
        int updated = jdbc.sql("""
                        update release_channel
                           set active_deployment_id = :activeDeploymentId,
                               updated_at = :updatedAt
                         where id = :id
                        """)
                .param("id", channel.id().value())
                .param("activeDeploymentId", channel.activeDeploymentId() == null
                        ? null
                        : channel.activeDeploymentId().value())
                .param("updatedAt", JdbcTime.toOffsetDateTime(channel.updatedAt()))
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            insert into release_channel (
                                id, project_id, environment, active_deployment_id, updated_at
                            )
                            values (
                                :id, :projectId, :environment, :activeDeploymentId, :updatedAt
                            )
                            """)
                    .param("id", channel.id().value())
                    .param("projectId", channel.projectId().value())
                    .param("environment", channel.environment())
                    .param("activeDeploymentId", channel.activeDeploymentId() == null
                            ? null
                            : channel.activeDeploymentId().value())
                    .param("updatedAt", JdbcTime.toOffsetDateTime(channel.updatedAt()))
                    .update();
        }
        return channel;
    }

    @Override
    public Optional<ReleaseChannel> findByProjectIdAndEnvironment(ProjectId projectId, String environment) {
        return jdbc.sql("""
                        select id, project_id, environment, active_deployment_id, updated_at
                          from release_channel
                         where project_id = :projectId
                           and environment = :environment
                        """)
                .param("projectId", projectId.value())
                .param("environment", environment)
                .query(this::map)
                .optional();
    }

    private ReleaseChannel map(ResultSet rs, int rowNum) throws SQLException {
        Object activeDeployment = rs.getObject("active_deployment_id");
        return ReleaseChannel.reconstitute(
                new ChannelId(JdbcUserAccountRepository.uuid(rs, "id")),
                new ProjectId(JdbcUserAccountRepository.uuid(rs, "project_id")),
                rs.getString("environment"),
                activeDeployment == null
                        ? null
                        : new DeploymentId(JdbcUserAccountRepository.uuid(rs, "active_deployment_id")),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
