package com.example.htmldeploy.application.deployment.port;

import java.util.List;

public interface ReleaseProviderCatalog {

    String defaultProvider();

    List<String> supportedProviders();

    String normalize(String provider);

    default boolean supports(String provider) {
        return provider != null && supportedProviders().contains(normalize(provider));
    }
}
