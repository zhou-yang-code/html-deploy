package com.example.htmldeploy.infrastructure.persistence.project;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.application.project.port.ProjectDeletionPort;
import com.example.htmldeploy.domain.project.model.ProjectId;

@Repository
public class JdbcProjectDeletionAdapter implements ProjectDeletionPort {

    private final JdbcClient jdbc;

    public JdbcProjectDeletionAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void deletePlatformData(ProjectId projectId) {
        jdbc.sql("delete from release_channel where project_id = :projectId")
                .param("projectId", projectId.value())
                .update();
        jdbc.sql("delete from deployment where project_id = :projectId")
                .param("projectId", projectId.value())
                .update();
        jdbc.sql("delete from artifact where project_id = :projectId")
                .param("projectId", projectId.value())
                .update();
        jdbc.sql("delete from outbox_event where project_id = :projectId")
                .param("projectId", projectId.value())
                .update();
        jdbc.sql("delete from project where id = :projectId")
                .param("projectId", projectId.value())
                .update();
    }
}
