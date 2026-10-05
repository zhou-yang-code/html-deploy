package com.example.htmldeploy.application.artifact.port;

import java.util.UUID;

public interface UploadTokenPort {

    UUID verify(String token);
}
