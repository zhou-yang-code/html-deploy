package com.example.htmldeploy.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.netlify")
public record NetlifyProperties(
        String apiBaseUrl,
        String authToken,
        String sitePrefix
) {
    public NetlifyProperties {
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.netlify.com/api/v1"
                : apiBaseUrl.replaceAll("/+$", "");
        sitePrefix = sitePrefix == null ? "" : sitePrefix.trim();
    }
}
