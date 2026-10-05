package com.example.htmldeploy.infrastructure.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.htmldeploy.application.project.port.ProjectResourceCleaner;
import com.example.htmldeploy.domain.shared.DomainException;
import com.example.htmldeploy.infrastructure.config.AppProperties;
import com.example.htmldeploy.infrastructure.config.NetlifyProperties;
import com.example.htmldeploy.infrastructure.config.ReleaseProperties;
import com.example.htmldeploy.infrastructure.storage.local.LocalPaths;
import com.example.htmldeploy.infrastructure.storage.netlify.NetlifySiteName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ProjectResourceCleanerAdapter implements ProjectResourceCleaner {

    private final AppProperties appProperties;
    private final NetlifyProperties netlifyProperties;
    private final ReleaseProperties releaseProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ProjectResourceCleanerAdapter(
            AppProperties appProperties,
            NetlifyProperties netlifyProperties,
            ReleaseProperties releaseProperties,
            ObjectMapper objectMapper
    ) {
        this.appProperties = appProperties;
        this.netlifyProperties = netlifyProperties;
        this.releaseProperties = releaseProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void deleteExternalResources(String tenantSlug, String projectSlug, UUID projectId) {
        deleteLocalFiles(tenantSlug, projectSlug, projectId);
        deleteNetlifySite(tenantSlug, projectSlug, projectId);
    }

    private void deleteLocalFiles(String tenantSlug, String projectSlug, UUID projectId) {
        try {
            String siteName = tenantSlug + "-" + projectSlug;
            LocalPaths.deleteRecursively(appProperties.nginxRoot().resolve(siteName));
            LocalPaths.deleteRecursively(appProperties.storageRoot().resolve("releases").resolve(projectId.toString()));
            LocalPaths.deleteRecursively(
                    appProperties.storageRoot().resolve("artifacts").resolve("projects").resolve(projectId.toString())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("failed to delete local project resources", exception);
        }
    }

    private void deleteNetlifySite(String tenantSlug, String projectSlug, UUID projectId) {
        if (!"netlify".equals(releaseProperties.provider())) {
            if (StringUtils.hasText(netlifyProperties.authToken())) {
                deleteNetlifySiteIfPresent(tenantSlug, projectSlug, projectId);
            }
            return;
        }
        if (!StringUtils.hasText(netlifyProperties.authToken())) {
            throw new DomainException("netlify.token_missing", "NETLIFY_AUTH_TOKEN is required to delete the site");
        }
        deleteNetlifySiteIfPresent(tenantSlug, projectSlug, projectId);
    }

    private void deleteNetlifySiteIfPresent(String tenantSlug, String projectSlug, UUID projectId) {
        String siteName = NetlifySiteName.from(
                netlifyProperties.sitePrefix(),
                tenantSlug,
                projectSlug,
                projectId
        );
        try {
            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(uri("/sites?name=" + URLEncoder.encode(siteName, StandardCharsets.UTF_8)))
                    .header("Authorization", "Bearer " + netlifyProperties.authToken())
                    .GET()
                    .build();
            HttpResponse<String> listResponse = httpClient.send(
                    listRequest,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (listResponse.statusCode() != 200) {
                throw new DomainException(
                        "netlify.http_error",
                        "Netlify site cleanup lookup failed (HTTP " + listResponse.statusCode() + ")"
                );
            }
            String siteId = findSiteId(objectMapper.readTree(listResponse.body()), siteName);
            if (siteId == null) {
                return;
            }
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(uri("/sites/" + siteId))
                    .header("Authorization", "Bearer " + netlifyProperties.authToken())
                    .DELETE()
                    .build();
            HttpResponse<String> deleteResponse = httpClient.send(
                    deleteRequest,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (deleteResponse.statusCode() != 200
                    && deleteResponse.statusCode() != 202
                    && deleteResponse.statusCode() != 204
                    && deleteResponse.statusCode() != 404) {
                throw new DomainException(
                        "netlify.http_error",
                        "Netlify site deletion failed (HTTP " + deleteResponse.statusCode() + ")"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Netlify project cleanup interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Netlify project cleanup failed", exception);
        }
    }

    private String findSiteId(JsonNode sites, String siteName) {
        if (!sites.isArray()) {
            return null;
        }
        for (JsonNode site : sites) {
            JsonNode name = site.get("name");
            if (name != null && !name.isNull() && siteName.equals(name.asText())) {
                JsonNode id = site.get("id");
                return id == null || id.isNull() ? null : id.asText();
            }
        }
        return null;
    }

    private URI uri(String path) {
        return URI.create(netlifyProperties.apiBaseUrl() + path);
    }
}
