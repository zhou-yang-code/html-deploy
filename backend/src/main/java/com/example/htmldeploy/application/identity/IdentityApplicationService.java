package com.example.htmldeploy.application.identity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.htmldeploy.application.identity.port.PasswordHasher;
import com.example.htmldeploy.application.identity.port.TokenIssuer;
import com.example.htmldeploy.application.identity.port.TokenIssuer.TokenPair;
import com.example.htmldeploy.application.shared.SlugGenerator;
import com.example.htmldeploy.domain.identity.model.Email;
import com.example.htmldeploy.domain.identity.model.Role;
import com.example.htmldeploy.domain.identity.model.Tenant;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.identity.model.TenantMember;
import com.example.htmldeploy.domain.identity.model.TenantSlug;
import com.example.htmldeploy.domain.identity.model.UserAccount;
import com.example.htmldeploy.domain.identity.model.UserId;
import com.example.htmldeploy.domain.identity.repository.MembershipRepository;
import com.example.htmldeploy.domain.identity.repository.TenantRepository;
import com.example.htmldeploy.domain.identity.repository.UserAccountRepository;
import com.example.htmldeploy.domain.shared.DomainException;

@Service
public class IdentityApplicationService {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    public IdentityApplicationService(
            UserAccountRepository users,
            TenantRepository tenants,
            MembershipRepository memberships,
            PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer
    ) {
        this.users = users;
        this.tenants = tenants;
        this.memberships = memberships;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthResult register(RegisterCommand command) {
        Email email = new Email(command.email());
        if (users.findByEmail(email).isPresent()) {
            throw new DomainException("identity.email_exists", "email is already registered");
        }
        String rawSlug = command.tenantSlug() == null || command.tenantSlug().isBlank()
                ? SlugGenerator.from(command.tenantName(), "tenant")
                : command.tenantSlug();
        TenantSlug slug = new TenantSlug(trimTo(rawSlug, 40));
        if (tenants.existsBySlug(slug)) {
            throw new DomainException("identity.tenant_slug_exists", "tenant slug is already in use");
        }
        UserAccount user = UserAccount.create(email, passwordHasher.hash(command.password()));
        Tenant tenant = Tenant.create(command.tenantName(), slug);
        users.save(user);
        tenants.save(tenant);
        memberships.save(TenantMember.create(tenant.id(), user.id(), Role.OWNER));
        return new AuthResult(tokenIssuer.issue(user), userSummary(user));
    }

    @Transactional(readOnly = true)
    public AuthResult login(LoginCommand command) {
        UserAccount user = users.findByEmail(new Email(command.email()))
                .orElseThrow(() -> new DomainException("identity.invalid_credentials", "invalid email or password"));
        user.requireActive();
        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw new DomainException("identity.invalid_credentials", "invalid email or password");
        }
        return new AuthResult(tokenIssuer.issue(user), userSummary(user));
    }

    @Transactional(readOnly = true)
    public AuthResult refresh(String refreshToken) {
        UserId userId = new UserId(tokenIssuer.parseRefreshToken(refreshToken));
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new DomainException("identity.user_not_found", "user not found"));
        user.requireActive();
        return new AuthResult(tokenIssuer.issue(user), userSummary(user));
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser(UserId userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new DomainException("identity.user_not_found", "user not found"));
        return userSummary(user);
    }

    @Transactional(readOnly = true)
    public List<TenantSummary> listTenants(UserId actorId) {
        return memberships.findByUserId(actorId).stream()
                .map(member -> {
                    Tenant tenant = tenants.findById(member.tenantId())
                            .orElseThrow(() -> new DomainException("identity.tenant_not_found", "tenant not found"));
                    return new TenantSummary(
                            tenant.id().value(),
                            tenant.name(),
                            tenant.slug().value(),
                            member.role()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantSummary tenantSummary(UserId actorId, TenantId tenantId) {
        requireTenantRole(actorId, tenantId, Role.OWNER, Role.MAINTAINER, Role.DEVELOPER, Role.VIEWER);
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new DomainException("identity.tenant_not_found", "tenant not found"));
        TenantMember member = memberships.findByTenantIdAndUserId(tenantId, actorId)
                .orElseThrow(() -> new AccessDeniedException("tenant access denied"));
        return new TenantSummary(
                tenant.id().value(),
                tenant.name(),
                tenant.slug().value(),
                member.role()
        );
    }

    @Transactional(readOnly = true)
    public TenantSummary tenantSummaryForWorker(TenantId tenantId) {
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new DomainException("identity.tenant_not_found", "tenant not found"));
        return new TenantSummary(tenant.id().value(), tenant.name(), tenant.slug().value(), Role.MAINTAINER);
    }

    @Transactional(readOnly = true)
    public List<MemberSummary> listMembers(UserId actorId, TenantId tenantId) {
        requireTenantRole(actorId, tenantId, Role.OWNER, Role.MAINTAINER, Role.DEVELOPER, Role.VIEWER);
        return memberships.findByTenantId(tenantId).stream()
                .map(member -> {
                    UserAccount user = users.findById(member.userId())
                            .orElseThrow(() -> new DomainException(
                                    "identity.user_not_found",
                                    "member user not found"
                            ));
                    return new MemberSummary(
                            user.id().value(),
                            user.email().value(),
                            member.role(),
                            member.createdAt()
                    );
                })
                .toList();
    }

    @Transactional
    public MemberSummary addMember(AddMemberCommand command) {
        requireTenantRole(command.actorId(), command.tenantId(), Role.OWNER, Role.MAINTAINER);
        if (command.role() == Role.OWNER) {
            requireTenantRole(command.actorId(), command.tenantId(), Role.OWNER);
        }
        UserAccount user = users.findByEmail(new Email(command.email()))
                .orElseThrow(() -> new DomainException(
                        "identity.user_not_found",
                        "the user must register before being added to a tenant"
                ));
        if (memberships.findByTenantIdAndUserId(command.tenantId(), user.id()).isPresent()) {
            throw new DomainException("identity.member_exists", "user is already a tenant member");
        }
        TenantMember member = memberships.save(TenantMember.create(command.tenantId(), user.id(), command.role()));
        return new MemberSummary(user.id().value(), user.email().value(), member.role(), member.createdAt());
    }

    @Transactional(readOnly = true)
    public void requireTenantRole(UserId actorId, TenantId tenantId, Role... requiredRoles) {
        TenantMember member = memberships.findByTenantIdAndUserId(tenantId, actorId)
                .orElseThrow(() -> new AccessDeniedException("tenant access denied"));
        if (member.role() == Role.OWNER) {
            return;
        }
        boolean allowed = Arrays.stream(requiredRoles).anyMatch(role -> role == member.role());
        if (!allowed) {
            throw new AccessDeniedException("insufficient tenant role");
        }
    }

    private UserSummary userSummary(UserAccount user) {
        List<TenantSummary> tenantSummaries = memberships.findByUserId(user.id()).stream()
                .map(member -> {
                    Tenant tenant = tenants.findById(member.tenantId())
                            .orElseThrow(() -> new DomainException("identity.tenant_not_found", "tenant not found"));
                    return new TenantSummary(
                            tenant.id().value(),
                            tenant.name(),
                            tenant.slug().value(),
                            member.role()
                    );
                })
                .toList();
        return new UserSummary(user.id().value(), user.email().value(), tenantSummaries);
    }

    private String trimTo(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length).replaceAll("-+$", "");
    }

    public record RegisterCommand(
            String email,
            String password,
            String tenantName,
            String tenantSlug
    ) {
    }

    public record LoginCommand(String email, String password) {
    }

    public record AddMemberCommand(
            UserId actorId,
            TenantId tenantId,
            String email,
            Role role
    ) {
    }

    public record AuthResult(TokenPair tokens, UserSummary user) {
    }

    public record UserSummary(
            java.util.UUID id,
            String email,
            List<TenantSummary> tenants
    ) {
    }

    public record TenantSummary(
            java.util.UUID id,
            String name,
            String slug,
            Role role
    ) {
    }

    public record MemberSummary(
            java.util.UUID userId,
            String email,
            Role role,
            Instant createdAt
    ) {
    }
}
