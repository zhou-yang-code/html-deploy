package com.example.htmldeploy.infrastructure.persistence.project;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.application.project.port.ProjectReleaseUrlPort;
import com.example.htmldeploy.domain.project.model.ProjectId;

@Repository
public class JdbcProjectReleaseUrlAdapter implements ProjectReleaseUrlPort {

    private final JdbcClient jdbc;

    public JdbcProjectReleaseUrlAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> activeReleaseUrl(ProjectId projectId) {
        return jdbc.sql("""
                        select d.release_path
                          from release_channel c
                          join deployment d on d.id = c.active_deployment_id
                         where c.project_id = :projectId
                           and d.release_path is not null
                         order by d.version desc
                         limit 1
                        """)
                .param("projectId", projectId.value())
                .query(String.class)
                .optional();
    }
}
