package com.example.htmldeploy.infrastructure.storage;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.deployment.port.ReleaseProviderCatalog;
import com.example.htmldeploy.domain.deployment.port.ReleasePublishRequest;
import com.example.htmldeploy.domain.deployment.port.ReleasePublisher;
import com.example.htmldeploy.domain.shared.DomainException;
import com.example.htmldeploy.infrastructure.storage.local.LocalReleasePublisher;
import com.example.htmldeploy.infrastructure.storage.netlify.NetlifyReleasePublisher;

@Primary
@Component
public class RoutingReleasePublisher implements ReleasePublisher {

    private final Map<String, ReleasePublisher> publishers = new LinkedHashMap<>();
    private final ReleaseProviderCatalog catalog;

    public RoutingReleasePublisher(
            LocalReleasePublisher localPublisher,
            NetlifyReleasePublisher netlifyPublisher,
            ReleaseProviderCatalog catalog
    ) {
        this.catalog = catalog;
        this.publishers.put(ReleaseProviderCatalogAdapter.LOCAL, localPublisher);
        this.publishers.put(ReleaseProviderCatalogAdapter.NETLIFY, netlifyPublisher);
    }

    @Override
    public String publish(ReleasePublishRequest request) {
        String provider = catalog.normalize(request.provider());
        ReleasePublisher publisher = publishers.get(provider);
        if (publisher == null) {
            throw new DomainException(
                    "deployment.provider_unsupported",
                    "unsupported release provider: " + request.provider()
            );
        }
        return publisher.publish(request);
    }
}
