package com.example.htmldeploy.infrastructure.storage;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.artifact.port.UploadTokenPort;

@Component
public class UploadTokenPortAdapter implements UploadTokenPort {

    private final UploadTokenService uploadTokenService;

    public UploadTokenPortAdapter(UploadTokenService uploadTokenService) {
        this.uploadTokenService = uploadTokenService;
    }

    @Override
    public UUID verify(String token) {
        return uploadTokenService.verify(token);
    }
}
