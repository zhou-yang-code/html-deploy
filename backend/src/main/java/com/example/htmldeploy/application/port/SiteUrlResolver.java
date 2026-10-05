package com.example.htmldeploy.application.port;

public interface SiteUrlResolver {

    String resolve(String tenantSlug, String projectSlug);
}
