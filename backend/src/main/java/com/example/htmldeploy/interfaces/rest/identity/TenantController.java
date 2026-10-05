package com.example.htmldeploy.interfaces.rest.identity;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.htmldeploy.application.identity.IdentityApplicationService;
import com.example.htmldeploy.application.identity.IdentityApplicationService.AddMemberCommand;
import com.example.htmldeploy.application.identity.IdentityApplicationService.MemberSummary;
import com.example.htmldeploy.application.identity.IdentityApplicationService.TenantSummary;
import com.example.htmldeploy.domain.identity.model.Role;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.interfaces.rest.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final IdentityApplicationService identity;

    public TenantController(IdentityApplicationService identity) {
        this.identity = identity;
    }

    @GetMapping
    List<TenantSummary> list(Authentication authentication) {
        return identity.listTenants(CurrentUser.id(authentication));
    }

    @GetMapping("/{tenantId}/members")
    List<MemberSummary> members(Authentication authentication, @PathVariable UUID tenantId) {
        return identity.listMembers(CurrentUser.id(authentication), new TenantId(tenantId));
    }

    @PostMapping("/{tenantId}/members")
    MemberSummary addMember(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @Valid @RequestBody AddMemberRequest request
    ) {
        return identity.addMember(new AddMemberCommand(
                CurrentUser.id(authentication),
                new TenantId(tenantId),
                request.email(),
                request.role()
        ));
    }

    public record AddMemberRequest(
            @Email @NotBlank String email,
            @NotNull Role role
    ) {
    }
}
