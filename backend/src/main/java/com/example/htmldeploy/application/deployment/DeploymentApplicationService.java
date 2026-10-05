package com.example.htmldeploy.application.deployment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.htmldeploy.application.artifact.ArtifactApplicationService;
import com.example.htmldeploy.application.port.DomainEventPublisher;
import com.example.htmldeploy.application.port.SiteUrlResolver;
import com.example.htmldeploy.application.project.ProjectApplicationService;
import com.example.htmldeploy.application.project.ProjectApplicationService.ProjectContext;
import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.artifact.port.ArtifactContentStore;
import com.example.htmldeploy.domain.deployment.event.DeploymentActivatedEvent;
import com.example.htmldeploy.domain.deployment.event.DeploymentFailedEvent;
import com.example.htmldeploy.domain.deployment.event.DeploymentRequestedEvent;
import com.example.htmldeploy.domain.deployment.model.Deployment;
import com.example.htmldeploy.domain.deployment.model.DeploymentId;
import com.example.htmldeploy.domain.deployment.model.DeploymentStatus;
import com.example.htmldeploy.domain.deployment.model.ReleaseChannel;
import com.example.htmldeploy.domain.deployment.port.ReleasePublishRequest;
import com.example.htmldeploy.domain.deployment.port.ReleasePublisher;
import com.example.htmldeploy.domain.deployment.repository.DeploymentRepository;
import com.example.htmldeploy.domain.deployment.repository.ReleaseChannelRepository;
import com.example.htmldeploy.domain.identity.model.Role;
import com.example.htmldeploy.domain.identity.model.UserId;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.shared.DomainException;

@Service
public class DeploymentApplicationService {

    private final DeploymentRepository deployments;
    private final ReleaseChannelRepository channels;
    private final ArtifactApplicationService artifacts;
    private final ArtifactContentStore contentStore;
    private final ReleasePublisher releasePublisher;
    private final ProjectApplicationService projects;
    private final SiteUrlResolver siteUrls;
    private final DomainEventPublisher events;
    private final TransactionTemplate transactions;

    public DeploymentApplicationService(
            DeploymentRepository deployments,
            ReleaseChannelRepository channels,
            ArtifactApplicationService artifacts,
            ArtifactContentStore contentStore,
            ReleasePublisher releasePublisher,
            ProjectApplicationService projects,
            SiteUrlResolver siteUrls,
            DomainEventPublisher events,
            TransactionTemplate transactions
    ) {
        this.deployments = deployments;
        this.channels = channels;
        this.artifacts = artifacts;
        this.contentStore = contentStore;
        this.releasePublisher = releasePublisher;
        this.projects = projects;
        this.siteUrls = siteUrls;
        this.events = events;
        this.transactions = transactions;
    }

    @Transactional
    public DeploymentDetails create(CreateDeploymentCommand command) {
        ProjectContext project = projects.requireProject(
                command.actorId(),
                command.projectId(),
                Role.DEVELOPER,
                Role.MAINTAINER
        );
        Artifact artifact = artifacts.requireReady(command.actorId(), command.artifactId(), command.projectId());
        Deployment deployment = createDeployment(project.project().id(), artifact.id(), command.environment());
        events.publish(DeploymentRequestedEvent.from(deployment, project.project().tenantId().value()));
        return details(deployment, project);
    }

    public void execute(DeploymentId deploymentId) {
        Deployment deployment = transactions.execute(status -> {
            Deployment current = findDeployment(deploymentId);
            if (current.status() == DeploymentStatus.ACTIVE || current.status() == DeploymentStatus.SUPERSEDED) {
                return current;
            }
            ProjectContext project = projects.requireProjectForWorker(current.projectId());
            Artifact artifact = artifacts.requireReadyForWorker(current.artifactId());
            current.begin();
            deployments.save(current);
            return current;
        });
        if (deployment == null
                || deployment.status() == DeploymentStatus.ACTIVE
                || deployment.status() == DeploymentStatus.SUPERSEDED) {
            return;
        }

        ProjectContext project = projects.requireProjectForWorker(deployment.projectId());
        Artifact artifact = artifacts.requireReadyForWorker(deployment.artifactId());
        try {
            Path contentDirectory = contentStore.contentPath(artifact.id());
            String releasePath = releasePublisher.publish(new ReleasePublishRequest(
                    project.project().tenantId().value(),
                    project.project().id().value(),
                    deployment.id().value(),
                    artifact.id().value(),
                    project.tenant().slug(),
                    project.project().slug().value(),
                    deployment.environment(),
                    contentDirectory
            ));
            transactions.executeWithoutResult(status -> {
                Deployment current = findDeployment(deploymentId);
                if (current.status() == DeploymentStatus.ACTIVE) {
                    return;
                }
                deployments.findActive(current.projectId(), current.environment())
                        .filter(active -> !active.id().equals(current.id()))
                        .ifPresent(active -> {
                            active.supersede();
                            deployments.save(active);
                        });
                current.activate(releasePath);
                deployments.save(current);
                ReleaseChannel channel = channels
                        .findByProjectIdAndEnvironment(current.projectId(), current.environment())
                        .orElseGet(() -> ReleaseChannel.create(current.projectId(), current.environment()));
                channel.activate(current.id());
                channels.save(channel);
                events.publish(DeploymentActivatedEvent.from(current, project.project().tenantId().value()));
            });
        } catch (Exception exception) {
            String errorCode = exception instanceof DomainException domainException
                    ? domainException.code()
                    : "deployment.publish_failed";
            transactions.executeWithoutResult(status -> {
                Deployment failed = findDeployment(deploymentId);
                failed.fail(errorCode);
                deployments.save(failed);
                events.publish(DeploymentFailedEvent.from(failed, project.project().tenantId().value()));
            });
            throw exception;
        }
    }

    @Transactional
    public DeploymentDetails rollback(RollbackCommand command) {
        Deployment target = findDeployment(command.targetDeploymentId());
        ProjectContext project = projects.requireProject(
                command.actorId(),
                target.projectId(),
                Role.DEVELOPER,
                Role.MAINTAINER
        );
        if (target.status() != DeploymentStatus.ACTIVE && target.status() != DeploymentStatus.SUPERSEDED) {
            throw new DomainException("deployment.rollback_unavailable", "target deployment has no successful release");
        }
        Artifact artifact = artifacts.requireReady(command.actorId(), target.artifactId(), target.projectId());
        Deployment deployment = createDeployment(target.projectId(), artifact.id(), target.environment());
        events.publish(DeploymentRequestedEvent.from(deployment, project.project().tenantId().value()));
        return details(deployment, project);
    }

    @Transactional(readOnly = true)
    public List<DeploymentDetails> list(UserId actorId, ProjectId projectId) {
        ProjectContext project = projects.requireProject(
                actorId,
                projectId,
                Role.VIEWER,
                Role.DEVELOPER,
                Role.MAINTAINER
        );
        return deployments.findByProjectId(projectId).stream()
                .map(deployment -> details(deployment, project))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeploymentDetails get(UserId actorId, DeploymentId deploymentId) {
        Deployment deployment = findDeployment(deploymentId);
        ProjectContext project = projects.requireProject(
                actorId,
                deployment.projectId(),
                Role.VIEWER,
                Role.DEVELOPER,
                Role.MAINTAINER
        );
        return details(deployment, project);
    }

    private Deployment createDeployment(ProjectId projectId, ArtifactId artifactId, String environment) {
        long version = deployments.nextVersion(projectId);
        return deployments.save(Deployment.create(projectId, artifactId, environment, version));
    }

    private Deployment findDeployment(DeploymentId deploymentId) {
        return deployments.findById(deploymentId)
                .orElseThrow(() -> new DomainException("deployment.not_found", "deployment not found"));
    }

    private DeploymentDetails details(Deployment deployment, ProjectContext project) {
        String publicUrl = deployment.releasePath() != null && deployment.releasePath().startsWith("http")
                ? deployment.releasePath()
                : siteUrls.resolve(project.tenant().slug(), project.project().slug().value());
        return new DeploymentDetails(
                deployment.id().value(),
                deployment.projectId().value(),
                deployment.artifactId().value(),
                deployment.environment(),
                deployment.version(),
                deployment.status(),
                deployment.releasePath(),
                deployment.errorCode(),
                publicUrl,
                deployment.createdAt(),
                deployment.updatedAt(),
                deployment.finishedAt()
        );
    }

    public record CreateDeploymentCommand(
            UserId actorId,
            ProjectId projectId,
            ArtifactId artifactId,
            String environment
    ) {
    }

    public record RollbackCommand(UserId actorId, DeploymentId targetDeploymentId) {
    }

    public record DeploymentDetails(
            java.util.UUID id,
            java.util.UUID projectId,
            java.util.UUID artifactId,
            String environment,
            long version,
            DeploymentStatus status,
            String releasePath,
            String errorCode,
            String url,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt
    ) {
    }
}
