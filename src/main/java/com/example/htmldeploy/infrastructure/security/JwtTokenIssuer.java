package com.example.htmldeploy.infrastructure.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.identity.port.TokenIssuer;
import com.example.htmldeploy.domain.identity.model.UserAccount;
import com.example.htmldeploy.infrastructure.config.SecurityProperties;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final SecurityProperties properties;

    public JwtTokenIssuer(
            JwtEncoder encoder,
            JwtDecoder decoder,
            SecurityProperties properties
    ) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
    }

    @Override
    public TokenPair issue(UserAccount user) {
        Instant now = Instant.now();
        String accessToken = encode(user, "access", now, now.plus(properties.accessTokenTtl()));
        String refreshToken = encode(user, "refresh", now, now.plus(properties.refreshTokenTtl()));
        return new TokenPair(accessToken, refreshToken, properties.accessTokenTtl().toSeconds());
    }

    @Override
    public UUID parseRefreshToken(String refreshToken) {
        var jwt = decoder.decode(refreshToken);
        if (!"refresh".equals(jwt.getClaimAsString("token_use"))) {
            throw new IllegalArgumentException("not a refresh token");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid refresh token subject", exception);
        }
    }

    private String encode(UserAccount user, String tokenUse, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().value().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("token_use", tokenUse)
                .claim("email", user.email().value())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
