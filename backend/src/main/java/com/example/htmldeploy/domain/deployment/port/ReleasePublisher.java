package com.example.htmldeploy.domain.deployment.port;

public interface ReleasePublisher {

    String publish(ReleasePublishRequest request);
}
