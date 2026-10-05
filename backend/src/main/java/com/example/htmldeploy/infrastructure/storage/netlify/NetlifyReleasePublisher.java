package com.example.htmldeploy.infrastructure.storage.netlify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.htmldeploy.domain.deployment.port.ReleasePublishRequest;
import com.example.htmldeploy.domain.deployment.port.ReleasePublisher;
import com.example.htmldeploy.domain.shared.DomainException;
import com.example.htmldeploy.infrastructure.config.NetlifyProperties;
import com.example.htmldeploy.infrastructure.config.ReleaseProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.release.provider", havingValue = "netlify")
public class NetlifyReleasePublisher implements ReleasePublisher {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final NetlifyProperties netlify;
    private final ReleaseProperties release;

    public NetlifyReleasePublisher(
            ObjectMapper objectMapper,
            NetlifyProperties netlify,
            ReleaseProperties release
    ) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = objectMapper;
        this.netlify = netlify;
        this.release = release;
    }

    @Override
    public String publish(ReleasePublishRequest request) {
        if (!StringUtils.hasText(netlify.authToken())) {
            throw new DomainException("netlify.token_missing", "NETLIFY_AUTH_TOKEN is not configured");
        }
        Path archive = null;
        try {
            Site site = resolveSite(siteName(request));
            archive = createArchive(request.artifactContentDirectory());
            JsonNode deploy = createDeploy(site.id(), archive);
            String deployId = text(deploy, "id");
            if (!StringUtils.hasText(deployId)) {
                throw new DomainException("netlify.invalid_response", "Netlify did not return a deploy id");
            }
            JsonNode ready = waitUntilReady(deployId);
            String url = firstText(ready, "ssl_url", "url", "deploy_ssl_url");
            if (!StringUtils.hasText(url)) {
                throw new DomainException("netlify.invalid_response", "Netlify did not return a public URL");
            }
            return url;
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException("netlify.publish_failed", "Netlify deployment failed: " + exception.getMessage());
        } finally {
            deleteQuietly(archive);
        }
    }

    private Site resolveSite(String siteName) throws IOException, InterruptedException {
        HttpResponse<String> existing = send(HttpRequest.newBuilder()
                .uri(uri("/sites?per_page=100"))
                .header("Authorization", authorization())
                .GET()
                .build());
        if (existing.statusCode() == 200) {
            JsonNode body = json(existing.body());
            if (body.isArray()) {
                for (JsonNode site : body) {
                    if (siteName.equals(text(site, "name"))) {
                        return new Site(text(site, "id"), text(site, "name"));
                    }
                }
            }
        }
        if (existing.statusCode() != 200 && existing.statusCode() != 404) {
            throw failure("Netlify site lookup failed", existing);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", siteName);
        HttpResponse<String> created = send(HttpRequest.newBuilder()
                .uri(uri("/sites"))
                .header("Authorization", authorization())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build());
        if (created.statusCode() != 200 && created.statusCode() != 201) {
            throw failure("Netlify site creation failed", created);
        }
        JsonNode body = json(created.body());
        return new Site(text(body, "id"), text(body, "name"));
    }

    private JsonNode createDeploy(String siteId, Path archive) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(uri("/sites/" + siteId + "/deploys"))
                .header("Authorization", authorization())
                .header("Content-Type", "application/zip")
                .POST(HttpRequest.BodyPublishers.ofFile(archive))
                .build());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw failure("Netlify deploy creation failed", response);
        }
        return json(response.body());
    }

    private JsonNode waitUntilReady(String deployId) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(release.publishTimeout());
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> response = send(HttpRequest.newBuilder()
                    .uri(uri("/deploys/" + deployId))
                    .header("Authorization", authorization())
                    .GET()
                    .build());
            if (response.statusCode() != 200) {
                throw failure("Netlify deploy status failed", response);
            }
            JsonNode body = json(response.body());
            String state = text(body, "state");
            if ("ready".equals(state)) {
                return body;
            }
            if ("error".equals(state) || "failed".equals(state)) {
                throw new DomainException("netlify.deploy_failed", "Netlify deploy state is " + state);
            }
            Thread.sleep(release.pollInterval().toMillis());
        }
        throw new DomainException("netlify.deploy_timeout", "Netlify deployment timed out");
    }

    private Path createArchive(Path sourceDirectory) throws IOException {
        if (!Files.isDirectory(sourceDirectory)) {
            throw new DomainException("netlify.content_missing", "artifact content directory does not exist");
        }
        Path archive = Files.createTempFile("netlify-deploy-", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            try (var paths = Files.walk(sourceDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList()) {
                    String name = sourceDirectory.relativize(path).toString().replace('\\', '/');
                    output.putNextEntry(new ZipEntry(name));
                    Files.copy(path, output);
                    output.closeEntry();
                }
            }
        }
        return archive;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create(netlify.apiBaseUrl() + path);
    }

    private String authorization() {
        return "Bearer " + netlify.authToken();
    }

    private JsonNode json(String body) throws IOException {
        return objectMapper.readTree(body);
    }

    private DomainException failure(String prefix, HttpResponse<String> response) {
        return new DomainException(
                "netlify.http_error",
                prefix + " (HTTP " + response.statusCode() + "): " + abbreviate(response.body())
        );
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String siteName(ReleasePublishRequest request) {
        String suffix = request.projectId().toString().substring(0, 8);
        String name = netlify.sitePrefix()
                + request.tenantSlug()
                + "-"
                + request.projectSlug()
                + "-"
                + suffix;
        name = name.toLowerCase().replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return name.length() > 63 ? name.substring(0, 63).replaceAll("-+$", "") : name;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private void deleteQuietly(Path archive) {
        if (archive == null) {
            return;
        }
        try {
            Files.deleteIfExists(archive);
        } catch (IOException ignored) {
            // The deployment result is more useful than temp cleanup failure.
        }
    }

    private record Site(String id, String name) {
    }
}
