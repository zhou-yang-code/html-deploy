package com.example.htmldeploy.infrastructure.storage.local;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.port.ContentFileResolver;
import com.example.htmldeploy.infrastructure.config.AppProperties;

@Component
public class LocalContentFileResolver implements ContentFileResolver {

    private final Path nginxRoot;

    public LocalContentFileResolver(AppProperties properties) {
        this.nginxRoot = properties.nginxRoot().toAbsolutePath().normalize();
    }

    @Override
    public Path resolve(String siteName, String relativePath) {
        if (siteName == null || !siteName.matches("^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$")) {
            throw new IllegalArgumentException("invalid site name");
        }
        Path siteRoot = nginxRoot.resolve(siteName).resolve("current").normalize();
        Path target = siteRoot.resolve(relativePath == null ? "" : relativePath).normalize();
        if (!target.startsWith(siteRoot)) {
            throw new IllegalArgumentException("invalid content path");
        }
        if (Files.isDirectory(target)) {
            target = target.resolve("index.html").normalize();
        }
        if (!Files.isRegularFile(target) && relativePath != null && !relativePath.contains(".")) {
            target = siteRoot.resolve("index.html").normalize();
        }
        return target;
    }
}
