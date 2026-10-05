package com.example.htmldeploy.application.port;

import com.example.htmldeploy.domain.shared.DomainEvent;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
