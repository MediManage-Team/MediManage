package org.example.MediManage.config;

import org.example.MediManage.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {
    private Path tempRoot;

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty(DatabaseConfig.DB_PATH_PROPERTY);
        System.clearProperty(AppPaths.INSTALL_ROOT_OVERRIDE_PROPERTY);
        System.clearProperty(AppPaths.APP_DATA_ROOT_OVERRIDE_PROPERTY);
        System.clearProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY);

        if (tempRoot != null) {
            deleteRecursively(tempRoot);
            tempRoot = null;
        }
    }

    @Test
    void linuxPackagedInstallUsesUserDataDatabaseAndSeedsStarterCopy() throws Exception {
        tempRoot = Files.createTempDirectory("db-config-linux-");
        Path installRoot = Files.createDirectories(tempRoot.resolve("install-root").resolve("runtime").resolve("db"));
        Path installDb = installRoot.resolve("medimanage.db");
        Path appDataRoot = tempRoot.resolve("linux-appdata");

        Files.writeString(installDb, "starter-db");

        System.setProperty(DatabaseConfig.DB_PATH_PROPERTY, "medimanage.db");
        System.setProperty(AppPaths.INSTALL_ROOT_OVERRIDE_PROPERTY, installRoot.getParent().getParent().toString());
        System.setProperty(AppPaths.APP_DATA_ROOT_OVERRIDE_PROPERTY, appDataRoot.toString());
        System.setProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY, "Linux");

        Path resolved = DatabaseConfig.getResolvedDatabaseFile().toPath();

        assertEquals(appDataRoot.resolve("runtime").resolve("db").resolve("medimanage.db"), resolved);
        assertTrue(Files.exists(resolved));
        assertEquals("starter-db", Files.readString(resolved));
    }

    @Test
    void windowsPackagedInstallKeepsDefaultDatabaseInsideInstallRoot() throws Exception {
        tempRoot = Files.createTempDirectory("db-config-windows-");
        Path installRoot = Files.createDirectories(tempRoot.resolve("install-root").resolve("runtime").resolve("db"));
        Path installDb = installRoot.resolve("medimanage.db");

        Files.writeString(installDb, "starter-db");

        System.setProperty(DatabaseConfig.DB_PATH_PROPERTY, "medimanage.db");
        System.setProperty(AppPaths.INSTALL_ROOT_OVERRIDE_PROPERTY, installRoot.getParent().getParent().toString());
        System.setProperty(AppPaths.APP_DATA_ROOT_OVERRIDE_PROPERTY, tempRoot.resolve("windows-appdata").toString());
        System.setProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY, "Windows 11");

        Path resolved = DatabaseConfig.getResolvedDatabaseFile().toPath();

        assertEquals(installDb, resolved);
        assertTrue(Files.exists(resolved));
    }

    private void deleteRecursively(Path root) throws Exception {
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
