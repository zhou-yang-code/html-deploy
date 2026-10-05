package com.example.htmldeploy.interfaces.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.artifact.ArtifactApplicationService;
import com.example.htmldeploy.application.deployment.DeploymentApplicationService;
import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.deployment.model.DeploymentId;
import com.example.htmldeploy.infrastructure.messaging.JdbcOutboxEventStore;
import com.example.htmldeploy.infrastructure.messaging.OutboxMessage;

@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final JdbcOutboxEventStore outbox;
    private final ArtifactApplicationService artifacts;
    private final DeploymentApplicationService deployments;

    public OutboxWorker(
            JdbcOutboxEventStore outbox,
            ArtifactApplicationService artifacts,
            DeploymentApplicationService deployments
    ) {
        this.outbox = outbox;
        this.artifacts = artifacts;
        this.deployments = deployments;
    }

    @Scheduled(fixedDelayString = "${app.worker.fixed-delay-ms:2000}", initialDelayString = "1000")
    public void processPendingEvents() {
        for (OutboxMessage message : outbox.findPending(20)) {
            try {
                handle(message);
                outbox.markPublished(message.id());
            } catch (Exception exception) {
                log.warn("Outbox event {} failed", message.eventType(), exception);
                outbox.markForRetry(message, exception.getMessage());
            }
        }
    }

    private void handle(OutboxMessage message) {
        switch (message.eventType()) {
            case "ArtifactUploaded" -> artifacts.validate(new ArtifactId(message.aggregateId()));
            case "DeploymentRequested" -> deployments.execute(new DeploymentId(message.aggregateId()));
            default -> {
                // Other events are integration facts consumed by future phases.
            }
        }
    }
}
