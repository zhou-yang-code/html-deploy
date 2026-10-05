package com.example.htmldeploy.interfaces.rest.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.htmldeploy.application.identity.IdentityApplicationService;
import com.example.htmldeploy.application.identity.IdentityApplicationService.AuthResult;
import com.example.htmldeploy.application.identity.IdentityApplicationService.LoginCommand;
import com.example.htmldeploy.application.identity.IdentityApplicationService.RegisterCommand;
import com.example.htmldeploy.application.identity.IdentityApplicationService.UserSummary;
import com.example.htmldeploy.interfaces.rest.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdentityApplicationService identity;

    public AuthController(IdentityApplicationService identity) {
        this.identity = identity;
    }

    @PostMapping("/register")
    AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return identity.register(new RegisterCommand(
                request.email(),
                request.password(),
                request.tenantName(),
                request.tenantSlug()
        ));
    }

    @PostMapping("/login")
    AuthResult login(@Valid @RequestBody LoginRequest request) {
        return identity.login(new LoginCommand(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    AuthResult refresh(@Valid @RequestBody RefreshRequest request) {
        return identity.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    UserSummary me(Authentication authentication) {
        return identity.currentUser(CurrentUser.id(authentication));
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 120) String tenantName,
            @Size(max = 40) String tenantSlug
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
