package com.example.htmldeploy.interfaces.rest.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import com.example.htmldeploy.application.port.ContentFileResolver;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ContentController {

    private static final Map<String, MediaType> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", MediaType.TEXT_HTML),
            Map.entry("css", MediaType.valueOf("text/css")),
            Map.entry("js", MediaType.valueOf("text/javascript")),
            Map.entry("json", MediaType.APPLICATION_JSON),
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("svg", MediaType.valueOf("image/svg+xml")),
            Map.entry("webp", MediaType.valueOf("image/webp")),
            Map.entry("ico", MediaType.valueOf("image/x-icon")),
            Map.entry("txt", MediaType.TEXT_PLAIN),
            Map.entry("woff", MediaType.valueOf("font/woff")),
            Map.entry("woff2", MediaType.valueOf("font/woff2"))
    );

    private final ContentFileResolver files;

    public ContentController(ContentFileResolver files) {
        this.files = files;
    }

    @GetMapping("/sites/{siteName}/**")
    public ResponseEntity<Resource> content(
            @PathVariable String siteName,
            HttpServletRequest request
    ) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String marker = "/sites/" + siteName + "/";
        String relativePath = fullPath != null && fullPath.startsWith(marker)
                ? fullPath.substring(marker.length())
                : "";
        Path file = files.resolve(siteName, relativePath);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = mediaType(file);
        CacheControl cacheControl = file.getFileName().toString().endsWith(".html")
                ? CacheControl.noCache().mustRevalidate()
                : CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(cacheControl)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    private MediaType mediaType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
        return CONTENT_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM);
    }
}
