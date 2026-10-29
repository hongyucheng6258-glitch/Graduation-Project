package com.campus.platform.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedOriginsConfigTest {

    @Test
    void adminDevServerOriginIsTrusted() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(yml.contains("http://localhost:5174"));
        assertTrue(yml.contains("http://127.0.0.1:5174"));
    }
}
