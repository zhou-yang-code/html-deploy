package com.example.htmldeploy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void parsesRenderPostgresConnectionString() {
        var parsed = DatabaseUrlEnvironmentPostProcessor.parse(
                "postgresql://html_user:p%40ssword@database.internal:5432/html_deploy?sslmode=require"
        );

        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://database.internal:5432/html_deploy?sslmode=require");
        assertThat(parsed.username()).isEqualTo("html_user");
        assertThat(parsed.password()).isEqualTo("p@ssword");
    }

    @Test
    void parsesRailwayPostgresConnectionString() {
        var parsed = DatabaseUrlEnvironmentPostProcessor.parse(
                "postgresql://postgres:secret@html-deploy-db.railway.internal:5432/railway"
        );

        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://html-deploy-db.railway.internal:5432/railway");
        assertThat(parsed.username()).isEqualTo("postgres");
        assertThat(parsed.password()).isEqualTo("secret");
    }
}
