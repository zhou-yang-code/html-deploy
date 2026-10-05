package com.example.htmldeploy.infrastructure.storage.netlify;

import java.util.UUID;

public final class NetlifySiteName {

    private NetlifySiteName() {
    }

    public static String from(String prefix, String tenantSlug, String projectSlug, UUID projectId) {
        String suffix = projectId.toString().substring(0, 8);
        String name = (prefix == null ? "" : prefix)
                + tenantSlug
                + "-"
                + projectSlug
                + "-"
                + suffix;
        name = name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return name.length() > 63
                ? name.substring(0, 63).replaceAll("-+$", "")
                : name;
    }
}
