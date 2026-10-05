package com.example.htmldeploy.interfaces.rest.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.htmldeploy.domain.identity.model.UserId;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UserId id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("authenticated JWT is required");
        }
        try {
            return new UserId(UUID.fromString(jwt.getSubject()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT subject is not a user id", exception);
        }
    }
}
