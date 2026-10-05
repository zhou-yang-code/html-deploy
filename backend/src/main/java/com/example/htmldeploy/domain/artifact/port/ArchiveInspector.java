package com.example.htmldeploy.domain.artifact.port;

import java.nio.file.Path;

import com.example.htmldeploy.domain.artifact.service.ArchivePolicy;

public interface ArchiveInspector {

    ArchiveInspection inspectAndExtract(Path zipFile, Path destination, ArchivePolicy policy);
}
