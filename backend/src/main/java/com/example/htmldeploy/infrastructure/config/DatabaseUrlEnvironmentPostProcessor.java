package com.example.htmldeploy.infrastructure.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (!StringUtils.hasText(databaseUrl) || StringUtils.hasText(environment.getProperty("DB_URL"))) {
            return;
        }
        DatabaseUrl parsed = parse(databaseUrl);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", parsed.jdbcUrl());
        if (StringUtils.hasText(parsed.username())) {
            properties.put("spring.datasource.username", parsed.username());
        }
        if (parsed.password() != null) {
            properties.put("spring.datasource.password", parsed.password());
        }
        environment.getPropertySources().addFirst(
                new MapPropertySource("renderDatabaseUrl", properties)
        );
    }

    static DatabaseUrl parse(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATABASE_URL is not a valid URI", exception);
        }
        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw new IllegalStateException("DATABASE_URL must use the postgres or postgresql scheme");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException("DATABASE_URL must contain a database host");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String database = uri.getPath() == null || uri.getPath().isBlank()
                ? "html_deploy"
                : uri.getPath().replaceFirst("^/", "");
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + database;
        if (StringUtils.hasText(uri.getRawQuery())) {
            jdbcUrl += "?" + uri.getRawQuery();
        }

        String username = null;
        String password = null;
        if (StringUtils.hasText(uri.getRawUserInfo())) {
            String[] credentials = uri.getRawUserInfo().split(":", 2);
            username = decode(credentials[0]);
            if (credentials.length == 2) {
                password = decode(credentials[1]);
            }
        }
        return new DatabaseUrl(jdbcUrl, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    record DatabaseUrl(String jdbcUrl, String username, String password) {
    }
}
