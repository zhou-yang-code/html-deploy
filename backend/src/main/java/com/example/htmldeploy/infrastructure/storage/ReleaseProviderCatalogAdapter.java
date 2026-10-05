package com.example.htmldeploy.infrastructure.storage;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.deployment.port.ReleaseProviderCatalog;
import com.example.htmldeploy.infrastructure.config.ReleaseProperties;

@Component
public class ReleaseProviderCatalogAdapter implements ReleaseProviderCatalog {

    public static final String LOCAL = "local";
    public static final String NETLIFY = "netlify";

    private static final List<String> SUPPORTED = List.of(NETLIFY, LOCAL);

    private static final Map<String, String> ALIASES = Map.of(
            "self-hosted", LOCAL,
            "selfhosted", LOCAL,
            "self_hosted", LOCAL,
            "self", LOCAL,
            "netlify.app", NETLIFY
    );

    private final ReleaseProperties release;

    public ReleaseProviderCatalogAdapter(ReleaseProperties release) {
        this.release = release;
    }

    @Override
    public String defaultProvider() {
        String configured = release.provider();
        if (configured == null || configured.isBlank()) {
            return LOCAL;
        }
        String provider = resolveAlias(configured.trim().toLowerCase(Locale.ROOT));
        return SUPPORTED.contains(provider) ? provider : LOCAL;
    }

    @Override
    public List<String> supportedProviders() {
        return SUPPORTED;
    }

    @Override
    public String normalize(String provider) {
        if (provider == null || provider.isBlank()) {
            return defaultProvider();
        }
        return resolveAlias(provider.trim().toLowerCase(Locale.ROOT));
    }

    private String resolveAlias(String value) {
        return ALIASES.getOrDefault(value, value);
    }
}
