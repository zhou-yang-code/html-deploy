package com.example.htmldeploy.application.port;

import java.nio.file.Path;

public interface ContentFileResolver {

    Path resolve(String siteName, String relativePath);
}
