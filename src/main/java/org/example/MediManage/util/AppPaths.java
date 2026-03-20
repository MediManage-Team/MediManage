package org.example.MediManage.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AppPaths {
    public static final String INSTALL_ROOT_OVERRIDE_PROPERTY = "medimanage.install.root";
    public static final String APP_DATA_ROOT_OVERRIDE_PROPERTY = "medimanage.app.data.root";
    public static final String OS_NAME_OVERRIDE_PROPERTY = "medimanage.os.name";

    private AppPaths() {
    }

    public static boolean isWindows() {
        return osName().contains("win");
    }

    public static boolean isLinux() {
        return osName().contains("linux");
    }

    public static Path appDataDir() {
        String override = System.getProperty(APP_DATA_ROOT_OVERRIDE_PROPERTY, "").trim();
        if (!override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }

        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "MediManage").toAbsolutePath().normalize();
            }
            return Path.of(System.getProperty("user.home"), "AppData", "Roaming", "MediManage")
                    .toAbsolutePath()
                    .normalize();
        }

        if (osName().contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "MediManage")
                    .toAbsolutePath()
                    .normalize();
        }

        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome, "MediManage").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "MediManage")
                .toAbsolutePath()
                .normalize();
    }

    public static Path appDataPath(String first, String... more) {
        return appDataDir().resolve(Path.of(first, more));
    }

    public static Path resolveInstallRoot() {
        String override = System.getProperty(INSTALL_ROOT_OVERRIDE_PROPERTY, "").trim();
        if (!override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }

        String jpackageAppPath = System.getProperty("jpackage.app-path", "").trim();
        if (!jpackageAppPath.isBlank()) {
            Path launcherPath = Path.of(jpackageAppPath).toAbsolutePath().normalize();
            Path parent = launcherPath.getParent();
            if (parent != null) {
                if (!isWindows() && "bin".equals(fileName(parent)) && parent.getParent() != null) {
                    return parent.getParent();
                }
                return parent;
            }
        }

        try {
            Path codeSource = Path.of(AppPaths.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .toAbsolutePath()
                    .normalize();

            if (Files.isRegularFile(codeSource) && codeSource.getFileName().toString().endsWith(".jar")) {
                return normalizeJarInstallRoot(codeSource.getParent());
            }

            if (Files.isDirectory(codeSource)) {
                if ("classes".equals(fileName(codeSource))
                        && codeSource.getParent() != null
                        && "target".equals(fileName(codeSource.getParent()))
                        && codeSource.getParent().getParent() != null) {
                    return codeSource.getParent().getParent();
                }
                return codeSource;
            }
        } catch (Exception ignored) {
        }

        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public static Path installPath(String first, String... more) {
        return resolveInstallRoot().resolve(Path.of(first, more));
    }

    public static boolean isPackagedInstall(Path root) {
        if (root == null) {
            return false;
        }
        if (!System.getProperty("jpackage.app-path", "").isBlank()) {
            return true;
        }
        return Files.isDirectory(root.resolve("runtime"))
                || Files.isDirectory(root.resolve("app"))
                || Files.isDirectory(root.resolve("lib").resolve("runtime"))
                || Files.isDirectory(root.resolve("lib").resolve("app"));
    }

    private static Path normalizeJarInstallRoot(Path jarParent) {
        if (jarParent == null) {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }

        if ("app".equals(fileName(jarParent))) {
            Path maybeLib = jarParent.getParent();
            if (maybeLib != null && "lib".equals(fileName(maybeLib)) && maybeLib.getParent() != null) {
                return maybeLib.getParent();
            }
            if (jarParent.getParent() != null) {
                return jarParent.getParent();
            }
        }

        if ("lib".equals(fileName(jarParent)) && jarParent.getParent() != null) {
            return jarParent.getParent();
        }

        return jarParent;
    }

    private static String osName() {
        return System.getProperty(OS_NAME_OVERRIDE_PROPERTY, System.getProperty("os.name", ""))
                .toLowerCase(Locale.ROOT);
    }

    private static String fileName(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
    }
}
