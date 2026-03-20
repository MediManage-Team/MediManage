package org.example.MediManage.config;

import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.util.AppPaths;

import java.io.File;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Centralized WhatsApp Bridge configuration.
 * All bridge URLs and paths are resolved here for consistent deployment.
 */
public class WhatsAppBridgeConfig {
    public static final String SERVER_PATH_OVERRIDE_PROPERTY = "medimanage.whatsapp.server.path";
    public static final String PORT_OVERRIDE_PROPERTY = "medimanage.whatsapp.bridge.port";
    public static final String APP_DATA_DIR_ENV = "MEDIMANAGE_APP_DATA_DIR";
    public static final String PUPPETEER_CACHE_DIR_ENV = "PUPPETEER_CACHE_DIR";

    private static final Preferences prefs = Preferences.userNodeForPackage(
            org.example.MediManage.MediManageApplication.class);

    /** Default bridge port */
    public static final int DEFAULT_PORT = 3000;
    public static final String HOST = "127.0.0.1";
    public static final String SERVICE_NAME = "medimanage-whatsapp-bridge";

    /** Get configured bridge port */
    public static int getPort() {
        String portOverride = System.getProperty(PORT_OVERRIDE_PROPERTY, "").trim();
        if (!portOverride.isBlank()) {
            try {
                return Integer.parseInt(portOverride);
            } catch (NumberFormatException ignored) {
            }
        }
        return prefs.getInt("whatsapp_bridge_port", DEFAULT_PORT);
    }

    /** Get base URL like http://localhost:3000 */
    public static String getBaseUrl() {
        return "http://" + HOST + ":" + getPort();
    }

    /** Full endpoint URLs */
    public static String statusUrl()   { return getBaseUrl() + "/status"; }
    public static String qrUrl()       { return getBaseUrl() + "/qr"; }
    public static String sendUrl()     { return getBaseUrl() + "/send"; }
    public static String logoutUrl()   { return getBaseUrl() + "/logout"; }
    public static String shutdownUrl() { return getBaseUrl() + "/shutdown"; }

    /**
     * Resolves the whatsapp-server directory, trying multiple locations.
     */
    public static File resolveServerDir() {
        String customPath = System.getProperty(SERVER_PATH_OVERRIDE_PROPERTY, "").trim();
        if (customPath.isBlank()) {
            customPath = prefs.get("whatsapp_server_path", "");
        }
        if (!customPath.isBlank()) {
            File customDir = new File(customPath);
            if (customDir.isDirectory()) return customDir;
        }

        File dir = AppPaths.installPath("whatsapp-server").toFile();
        if (isServerDirectory(dir)) {
            return shouldMirrorPackagedServer(dir) ? prepareRuntimeServerDir(dir) : dir;
        }

        File runtimeDir = AppPaths.appDataPath("whatsapp-server").toFile();
        if (isServerDirectory(runtimeDir)) {
            return runtimeDir;
        }

        dir = new File("whatsapp-server");
        if (dir.isDirectory()) return dir;

        String userDir = System.getProperty("user.dir");
        dir = new File(userDir, "whatsapp-server");
        if (dir.isDirectory()) return dir;

        dir = new File(userDir, "../whatsapp-server");
        if (dir.isDirectory()) return dir;

        dir = new File(System.getProperty("user.home"), "MediManage/whatsapp-server");
        if (dir.isDirectory()) return dir;

        return null;
    }

    /**
     * Resolves the Node.js executable, preferring the bundled runtime/node/node.exe.
     */
    public static String resolveNodeExe() {
        File serverDir = resolveServerDir();
        if (serverDir != null) {
            for (String candidate : nodeExecutableCandidates()) {
                File bundledNode = new File(serverDir, candidate);
                if (bundledNode.isFile()) {
                    return bundledNode.getAbsolutePath();
                }
            }
        }
        return AppPaths.isWindows() ? "node.exe" : "node";
    }

    public static String resolveNpmCommand() {
        return AppPaths.isWindows() ? "npm.cmd" : "npm";
    }

    /**
     * Resolves the entry script: protected start_protected.js or raw index.js.
     */
    public static String resolveEntryScript(File serverDir) {
        File rawEntry = new File(serverDir, "index.js");
        if (rawEntry.exists()) {
            return "index.js";
        }
        File protectedEntry = new File(serverDir, "start_protected.js");
        if (protectedEntry.exists()) {
            return "start_protected.js";
        }
        return "index.js";
    }

    public static ProcessBuilder createDependencyInstallProcessBuilder(File serverDir) {
        String installCommand = new File(serverDir, "package-lock.json").isFile() ? "ci" : "install";
        ProcessBuilder builder = new ProcessBuilder(resolveNpmCommand(), installCommand, "--omit=dev");
        builder.directory(serverDir);
        builder.redirectErrorStream(true);
        applyRuntimeEnvironment(builder);
        return builder;
    }

    public static ProcessBuilder createBrowserInstallProcessBuilder(File serverDir) {
        File installScript = resolvePuppeteerInstallScript(serverDir);
        if (installScript == null) {
            throw new IllegalStateException("Puppeteer install script not found in whatsapp-server/node_modules.");
        }

        ProcessBuilder builder = new ProcessBuilder(resolveNodeExe(), installScript.getAbsolutePath());
        builder.directory(serverDir);
        builder.redirectErrorStream(true);
        applyRuntimeEnvironment(builder);
        return builder;
    }

    public static ProcessBuilder createStartProcessBuilder() {
        File serverDir = resolveServerDir();
        if (serverDir == null || !serverDir.isDirectory()) {
            throw new IllegalStateException("whatsapp-server directory not found.");
        }

        ProcessBuilder builder = new ProcessBuilder(resolveNodeExe(), resolveEntryScript(serverDir));
        builder.directory(serverDir);
        builder.redirectErrorStream(true);
        builder.environment().put("PORT", String.valueOf(getPort()));
        builder.environment().put("HOST", HOST);
        builder.environment().put(LocalAdminTokenManager.ENV_NAME, LocalAdminTokenManager.getOrCreateToken());
        applyRuntimeEnvironment(builder);
        return builder;
    }

    public static void applyAdminHeader(HttpRequest.Builder builder) {
        LocalAdminTokenManager.applyHeader(builder);
    }

    /**
     * Check if Node.js is available on this system (bundled or system-installed).
     */
    public static boolean isNodeAvailable() {
        try {
            String nodeExe = resolveNodeExe();
            Process p = new ProcessBuilder(nodeExe, "--version")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int exitCode = p.waitFor();
            return exitCode == 0 && output.startsWith("v");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the bridge server is already running on the configured port.
     */
    public static boolean isRunning() {
        try (java.net.Socket ignored = new java.net.Socket(HOST, getPort())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNpmAvailable() {
        try {
            Process p = new ProcessBuilder(resolveNpmCommand(), "--version")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isBrowserAvailable(File serverDir) {
        if (serverDir == null || !serverDir.isDirectory()) {
            return false;
        }

        for (String candidate : browserCandidates()) {
            if (new File(candidate).isFile()) {
                return true;
            }
        }

        try {
            String nodeExe = resolveNodeExe();
            String script = """
                    const fs = require('fs');
                    const puppeteer = require('puppeteer');
                    try {
                      const executablePath = puppeteer.executablePath();
                      if (executablePath && fs.existsSync(executablePath)) {
                        console.log(executablePath);
                        process.exit(0);
                      }
                    } catch (error) {
                      console.error(String(error));
                    }
                    process.exit(1);
                    """;
            Process process = new ProcessBuilder(nodeExe, "-e", script)
                    .directory(serverDir)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isServerDirectory(File dir) {
        return dir != null
                && dir.isDirectory()
                && (new File(dir, "index.js").isFile() || new File(dir, "start_protected.js").isFile());
    }

    private static boolean shouldMirrorPackagedServer(File sourceDir) {
        Path installRoot = AppPaths.resolveInstallRoot();
        return AppPaths.isPackagedInstall(installRoot)
                && !AppPaths.isWindows()
                && sourceDir.toPath().toAbsolutePath().normalize().startsWith(installRoot);
    }

    private static File prepareRuntimeServerDir(File sourceDir) {
        Path runtimeDir = AppPaths.appDataPath("whatsapp-server");
        try {
            Files.createDirectories(runtimeDir);
            for (String name : List.of(
                    "index.js",
                    "index.jsc",
                    "security.js",
                    "start_protected.js",
                    "package.json",
                    "package-lock.json",
                    ".env",
                    "node",
                    "node.exe",
                    "bin/node",
                    "bin/node.exe")) {
                copyIfPresent(sourceDir.toPath().resolve(name), runtimeDir.resolve(name));
            }

            Path sourceModules = sourceDir.toPath().resolve("node_modules");
            Path runtimeModules = runtimeDir.resolve("node_modules");
            if (Files.isDirectory(sourceModules) && !Files.exists(runtimeModules)) {
                copyDirectory(sourceModules, runtimeModules);
            }
            return runtimeDir.toFile();
        } catch (Exception e) {
            return sourceDir;
        }
    }

    private static void copyIfPresent(Path source, Path target) throws java.io.IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void copyDirectory(Path sourceDir, Path targetDir) throws java.io.IOException {
        try (var stream = Files.walk(sourceDir)) {
            for (Path source : stream.toList()) {
                Path relative = sourceDir.relativize(source);
                Path target = targetDir.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static List<String> browserCandidates() {
        if (AppPaths.isWindows()) {
            return List.of(
                    valueOrEmpty(System.getenv("PUPPETEER_EXECUTABLE_PATH")),
                    valueOrEmpty(System.getenv("CHROME_BIN")),
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
        }

        return List.of(
                valueOrEmpty(System.getenv("PUPPETEER_EXECUTABLE_PATH")),
                valueOrEmpty(System.getenv("CHROME_BIN")),
                "/usr/bin/google-chrome",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/snap/bin/chromium",
                "/usr/bin/microsoft-edge",
                "/usr/bin/microsoft-edge-stable");
    }

    private static List<String> nodeExecutableCandidates() {
        if (AppPaths.isWindows()) {
            return List.of("node.exe", "node", "bin/node.exe", "bin/node");
        }
        return List.of("node", "bin/node");
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void applyRuntimeEnvironment(ProcessBuilder builder) {
        builder.environment().put(APP_DATA_DIR_ENV, AppPaths.appDataDir().toString());
        builder.environment().put(PUPPETEER_CACHE_DIR_ENV, AppPaths.appDataPath("puppeteer-cache").toString());
    }

    private static File resolvePuppeteerInstallScript(File serverDir) {
        if (serverDir == null || !serverDir.isDirectory()) {
            return null;
        }

        for (String candidate : List.of(
                "node_modules/puppeteer/install.mjs",
                "node_modules/puppeteer/lib/cjs/puppeteer/node/cli.js")) {
            File script = new File(serverDir, candidate);
            if (script.isFile()) {
                return script;
            }
        }

        return null;
    }
}
