package com.example.htmldeploy.infrastructure.config;

import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.port.SiteUrlResolver;

@Component
public class ConfiguredSiteUrlResolver implements SiteUrlResolver {

    private final AppProperties properties;

    public ConfiguredSiteUrlResolver(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(String tenantSlug, String projectSlug) {
        return properties.siteUrlTemplate()
                .replace("{tenantSlug}", tenantSlug)
                .replace("{projectSlug}", projectSlug)
                .replace("{site}", tenantSlug + "-" + projectSlug);
    }
}
