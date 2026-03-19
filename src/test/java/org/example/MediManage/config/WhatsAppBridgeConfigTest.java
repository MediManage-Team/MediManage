package org.example.MediManage.config;

import org.example.MediManage.security.LocalAdminTokenManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppBridgeConfigTest {
    private final String originalUserDir = System.getProperty("user.dir");
    private Path tempRoot;

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty(WhatsAppBridgeConfig.SERVER_PATH_OVERRIDE_PROPERTY);
        System.clearProperty(WhatsAppBridgeConfig.PORT_OVERRIDE_PROPERTY);
        System.setProperty("user.dir", originalUserDir);
        if (tempRoot != null) {
            deleteRecursively(tempRoot);
            tempRoot = null;
        }
    }

    @Test
    void createStartProcessBuilderUsesConfiguredPackagedBridgePath() throws Exception {
        tempRoot = Files.createTempDirectory("bridge-config-test-");
        Path isolatedUserDir = Files.createDirectory(tempRoot.resolve("isolated-user-dir"));
        Path serverDir = Files.createDirectory(tempRoot.resolve("custom-whatsapp-server"));
        Files.createFile(serverDir.resolve("node.exe"));
        Files.createFile(serverDir.resolve("index.js"));
        Files.createFile(serverDir.resolve("start_protected.js"));

        System.setProperty(WhatsAppBridgeConfig.SERVER_PATH_OVERRIDE_PROPERTY, serverDir.toString());
        System.setProperty(WhatsAppBridgeConfig.PORT_OVERRIDE_PROPERTY, "4123");
        System.setProperty("user.dir", isolatedUserDir.toString());

        ProcessBuilder builder = WhatsAppBridgeConfig.createStartProcessBuilder();

        assertEquals(serverDir.toFile(), builder.directory());
        assertEquals(2, builder.command().size());
        assertTrue(builder.command().get(0).endsWith("node.exe"));
        assertEquals("index.js", builder.command().get(1));
        assertEquals("4123", builder.environment().get("PORT"));
        assertEquals("127.0.0.1", builder.environment().get("HOST"));
        assertFalse(builder.environment().getOrDefault(LocalAdminTokenManager.ENV_NAME, "").isBlank());
    }

    @Test
    void shutdownUrlUsesConfiguredPort() {
        System.setProperty(WhatsAppBridgeConfig.PORT_OVERRIDE_PROPERTY, "4555");

        assertEquals("http://127.0.0.1:4555/shutdown", WhatsAppBridgeConfig.shutdownUrl());
    }

    private void deleteRecursively(Path root) throws Exception {
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }
}
