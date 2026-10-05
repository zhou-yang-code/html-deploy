package com.example.htmldeploy.infrastructure.storage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.example.htmldeploy.infrastructure.config.AppProperties;

@Component
public class UploadTokenService {

    private final byte[] secret;

    public UploadTokenService(AppProperties properties) {
        this.secret = properties.uploadTokenSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID artifactId, Instant expiresAt) {
        String payload = artifactId + ":" + expiresAt.getEpochSecond();
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    public UUID verify(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid upload token");
            }
            String payload = parts[0] + ":" + parts[1];
            if (!Instant.now().isBefore(Instant.ofEpochSecond(Long.parseLong(parts[1])))) {
                throw new IllegalArgumentException("upload token expired");
            }
            if (!constantTimeEquals(sign(payload), parts[2])) {
                throw new IllegalArgumentException("invalid upload token signature");
            }
            return UUID.fromString(parts[0]);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid upload token", exception);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to sign upload token", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < leftBytes.length; index++) {
            result |= leftBytes[index] ^ rightBytes[index];
        }
        return result == 0;
    }
}
