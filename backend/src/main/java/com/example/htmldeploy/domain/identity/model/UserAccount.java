package com.example.htmldeploy.domain.identity.model;

import java.time.Instant;

public final class UserAccount {

    private final UserId id;
    private final Email email;
    private final String passwordHash;
    private UserStatus status;
    private final Instant createdAt;

    private UserAccount(
            UserId id,
            Email email,
            String passwordHash,
            UserStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static UserAccount create(Email email, String passwordHash) {
        return new UserAccount(UserId.newId(), email, passwordHash, UserStatus.ACTIVE, Instant.now());
    }

    public static UserAccount reconstitute(
            UserId id,
            Email email,
            String passwordHash,
            UserStatus status,
            Instant createdAt
    ) {
        return new UserAccount(id, email, passwordHash, status, createdAt);
    }

    public void requireActive() {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("user is not active");
        }
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
