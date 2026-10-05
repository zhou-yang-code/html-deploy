package com.example.htmldeploy.application.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void normalizesLatinText() {
        assertThat(SlugGenerator.from("Acme Studio 2026", "tenant")).isEqualTo("acme-studio-2026");
    }

    @Test
    void createsFallbackForNonAsciiText() {
        assertThat(SlugGenerator.from("活动页面", "project")).startsWith("project-");
    }
}
