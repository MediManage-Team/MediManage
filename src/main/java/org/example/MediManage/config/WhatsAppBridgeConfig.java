package org.example.MediManage.config;

import org.example.MediManage.security.LocalAdminTokenManager;

import java.io.File;
import java.net.http.HttpRequest;
import java.util.prefs.Preferences;

/**
 * Centralized WhatsApp Bridge configuration.
 * All bridge URLs and paths are resolved here for consistent deployment.
 */
public class WhatsAppBridgeConfig {

    private static final Preferences prefs = Preferences.userNodeForPackage(
            org.example.MediManage.MediManageApplication.class);

    /** Default bridge port */
    public static final int DEFAULT_PORT = 3000;
    public static final String HOST = "127.0.0.1";
    public static final String SERVICE_NAME = "medimanage-whatsapp-bridge";

    /** Get configured bridge port */
    public static int getPort() {
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
        String customPath = prefs.get("whatsapp_server_path", "");
        if (!customPath.isBlank()) {
            File customDir = new File(customPath);
            if (customDir.isDirectory()) return customDir;
        }

        File dir = new File("whatsapp-server");
        if (dir.isDirectory()) return dir;

        String userDir = System.getProperty("user.dir");
        dir = new File(userDir, "whatsapp-server");
        if (dir.isDirectory()) return dir;

        dir = new File(userDir, "../whatsapp-server");
        if (dir.isDirectory()) return dir;

        String appData = System.getenv("APPDATA");
        if (appData != null) {
            dir = new File(appData, "MediManage/whatsapp-server");
            if (dir.isDirectory()) return dir;
        }

        dir = new File(System.getProperty("user.home"), "MediManage/whatsapp-server");
        if (dir.isDirectory()) return dir;

        return null;
    }

    /**
     * Resolves the Node.js executable, preferring the bundled runtime/node/node.exe.
     */
    public static String resolveNodeExe() {
        // Check bundled node.exe inside the whatsapp-server directory first
        File serverDir = resolveServerDir();
        if (serverDir != null) {
            File bundledNode = new File(serverDir, "node.exe");
            if (bundledNode.exists()) {
                return bundledNode.getAbsolutePath();
            }
        }
        return "node";
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
}
