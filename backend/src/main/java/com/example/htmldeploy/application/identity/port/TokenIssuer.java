package com.example.htmldeploy.application.identity.port;

import java.util.UUID;

import com.example.htmldeploy.domain.identity.model.UserAccount;

public interface TokenIssuer {

    TokenPair issue(UserAccount user);

    UUID parseRefreshToken(String refreshToken);

    record TokenPair(
            String accessToken,
            String refreshToken,
            long expiresInSeconds
    ) {
    }
}
