package com.example.htmldeploy.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.htmldeploy.domain.artifact.service.ArchivePolicy;
import com.example.htmldeploy.domain.artifact.service.ArchiveValidationService;

@Configuration
public class ArchiveConfiguration {

    @Bean
    ArchivePolicy archivePolicy(AppProperties properties) {
        return new ArchivePolicy(
                properties.archiveLimits().maxZipBytes(),
                properties.archiveLimits().maxExpandedBytes(),
                properties.archiveLimits().maxFiles(),
                properties.archiveLimits().maxPathLength(),
                "index.html"
        );
    }

    @Bean
    ArchiveValidationService archiveValidationService() {
        return new ArchiveValidationService();
    }
}
