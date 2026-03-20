package org.example.MediManage.config;

import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.util.AppPaths;
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
        System.clearProperty(AppPaths.INSTALL_ROOT_OVERRIDE_PROPERTY);
        System.clearProperty(AppPaths.APP_DATA_ROOT_OVERRIDE_PROPERTY);
        System.clearProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY);
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
        assertFalse(builder.environment().getOrDefault(WhatsAppBridgeConfig.APP_DATA_DIR_ENV, "").isBlank());
        assertFalse(builder.environment().getOrDefault(WhatsAppBridgeConfig.PUPPETEER_CACHE_DIR_ENV, "").isBlank());
    }

    @Test
    void createStartProcessBuilderMirrorsPackagedBridgeIntoLinuxUserData() throws Exception {
        tempRoot = Files.createTempDirectory("bridge-linux-config-test-");
        Path installRoot = Files.createDirectories(tempRoot.resolve("install-root").resolve("runtime"));
        Path packagedServerDir = Files.createDirectories(installRoot.getParent().resolve("whatsapp-server"));
        Path appDataRoot = tempRoot.resolve("linux-appdata");

        Files.writeString(packagedServerDir.resolve("index.js"), "console.log('linux bridge');");
        Files.writeString(packagedServerDir.resolve("security.js"), "module.exports = {};");
        Files.writeString(packagedServerDir.resolve("package.json"), "{\"name\":\"bridge\"}");
        Files.writeString(packagedServerDir.resolve("package-lock.json"), "{}");

        System.setProperty(AppPaths.INSTALL_ROOT_OVERRIDE_PROPERTY, installRoot.getParent().toString());
        System.setProperty(AppPaths.APP_DATA_ROOT_OVERRIDE_PROPERTY, appDataRoot.toString());
        System.setProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY, "Linux");
        System.setProperty(WhatsAppBridgeConfig.PORT_OVERRIDE_PROPERTY, "5123");

        ProcessBuilder builder = WhatsAppBridgeConfig.createStartProcessBuilder();
        Path mirroredDir = appDataRoot.resolve("whatsapp-server");

        assertEquals(mirroredDir.toFile(), builder.directory());
        assertEquals("node", builder.command().get(0));
        assertEquals("index.js", builder.command().get(1));
        assertEquals(appDataRoot.toString(), builder.environment().get(WhatsAppBridgeConfig.APP_DATA_DIR_ENV));
        assertEquals(appDataRoot.resolve("puppeteer-cache").toString(),
                builder.environment().get(WhatsAppBridgeConfig.PUPPETEER_CACHE_DIR_ENV));
        assertTrue(Files.isRegularFile(mirroredDir.resolve("index.js")));
        assertTrue(Files.isRegularFile(mirroredDir.resolve("package.json")));
    }

    @Test
    void createBrowserInstallProcessBuilderUsesDirectPuppeteerInstallScript() throws Exception {
        tempRoot = Files.createTempDirectory("bridge-browser-install-test-");
        Path serverDir = Files.createDirectories(tempRoot.resolve("whatsapp-server"));
        Path puppeteerDir = Files.createDirectories(serverDir.resolve("node_modules").resolve("puppeteer"));

        Files.writeString(serverDir.resolve("index.js"), "console.log('bridge');");
        Files.writeString(puppeteerDir.resolve("install.mjs"), "console.log('install');");
        System.setProperty(WhatsAppBridgeConfig.SERVER_PATH_OVERRIDE_PROPERTY, serverDir.toString());
        System.setProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY, "Linux");

        ProcessBuilder builder = WhatsAppBridgeConfig.createBrowserInstallProcessBuilder(serverDir.toFile());

        assertEquals(serverDir.toFile(), builder.directory());
        assertEquals("node", builder.command().get(0));
        assertEquals(puppeteerDir.resolve("install.mjs").toAbsolutePath().toString(), builder.command().get(1));
        assertFalse(builder.environment().getOrDefault(WhatsAppBridgeConfig.APP_DATA_DIR_ENV, "").isBlank());
        assertFalse(builder.environment().getOrDefault(WhatsAppBridgeConfig.PUPPETEER_CACHE_DIR_ENV, "").isBlank());
    }

    @Test
    void createStartProcessBuilderIgnoresWindowsNodeBinaryOnLinux() throws Exception {
        tempRoot = Files.createTempDirectory("bridge-linux-node-test-");
        Path serverDir = Files.createDirectories(tempRoot.resolve("whatsapp-server"));

        Files.writeString(serverDir.resolve("index.js"), "console.log('bridge');");
        Files.writeString(serverDir.resolve("node"), "#!/bin/sh\nexit 0\n");
        Files.writeString(serverDir.resolve("node.exe"), "windows-node");

        System.setProperty(WhatsAppBridgeConfig.SERVER_PATH_OVERRIDE_PROPERTY, serverDir.toString());
        System.setProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY, "Linux");

        ProcessBuilder builder = WhatsAppBridgeConfig.createStartProcessBuilder();

        assertEquals(serverDir.resolve("node").toAbsolutePath().toString(), builder.command().get(0));
        assertEquals("index.js", builder.command().get(1));
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
