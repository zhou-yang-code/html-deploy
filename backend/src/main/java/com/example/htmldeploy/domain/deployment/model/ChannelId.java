package com.example.htmldeploy.domain.deployment.model;

import java.util.Objects;
import java.util.UUID;

public record ChannelId(UUID value) {

    public ChannelId {
        Objects.requireNonNull(value, "channelId must not be null");
    }

    public static ChannelId newId() {
        return new ChannelId(UUID.randomUUID());
    }
}
