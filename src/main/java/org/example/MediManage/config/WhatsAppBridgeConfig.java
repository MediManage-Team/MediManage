package org.example.MediManage.config;

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

    /** Get configured bridge port */
    public static int getPort() {
        return prefs.getInt("whatsapp_bridge_port", DEFAULT_PORT);
    }

    /** Get base URL like http://localhost:3000 */
    public static String getBaseUrl() {
        return "http://localhost:" + getPort();
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
    public static java.io.File resolveServerDir() {
        java.io.File dir = new java.io.File("whatsapp-server");
        if (dir.isDirectory()) return dir;

        String userDir = System.getProperty("user.dir");
        dir = new java.io.File(userDir, "whatsapp-server");
        if (dir.isDirectory()) return dir;

        dir = new java.io.File(userDir, "../whatsapp-server");
        if (dir.isDirectory()) return dir;

        String appData = System.getenv("APPDATA");
        if (appData != null) {
            dir = new java.io.File(appData, "MediManage/whatsapp-server");
            if (dir.isDirectory()) return dir;
        }

        dir = new java.io.File(System.getProperty("user.home"), "MediManage/whatsapp-server");
        if (dir.isDirectory()) return dir;

        String customPath = prefs.get("whatsapp_server_path", "");
        if (!customPath.isBlank()) {
            dir = new java.io.File(customPath);
            if (dir.isDirectory()) return dir;
        }

        return null;
    }

    /**
     * Check if Node.js is available on this system.
     */
    public static boolean isNodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version")
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
        try (java.net.Socket ignored = new java.net.Socket("127.0.0.1", getPort())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
