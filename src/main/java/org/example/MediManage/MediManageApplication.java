package org.example.MediManage;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.MediManage.config.WhatsAppBridgeConfig;
import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.service.AdminBootstrapService;
import org.example.MediManage.service.sidecar.SidecarHttpProbe;
import org.example.MediManage.service.sidecar.SidecarOwnershipMetadata;
import org.example.MediManage.service.sidecar.SidecarProbeResult;
import org.example.MediManage.service.sidecar.SidecarStartupAdvisor;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.controller.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MediManageApplication extends Application {
        private static final Logger LOGGER = Logger.getLogger(MediManageApplication.class.getName());
        private static final String AI_SERVICE_NAME = "medimanage-ai-engine";
        private static final int AI_PORT = 5000;

        private static MediManageApplication instance;
        private volatile Process pythonProcess;
        private volatile Process mcpProcess;
        private volatile Process whatsappBridgeProcess;

        public static MediManageApplication getInstance() {
                return instance;
        }

        @Override
        public void start(Stage stage) throws Exception {
                instance = this;

                // Load Cascadia Code font family (bundled TTFs)
                Font.loadFont(getClass().getResourceAsStream("/org/example/MediManage/fonts/CascadiaCode.ttf"), 13);
                Font.loadFont(getClass().getResourceAsStream("/org/example/MediManage/fonts/CascadiaMono.ttf"), 13);

                // Apply AtlantaFX Theme immediately
                Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());

                if (!initializeDatabase()) {
                        return;
                }

                if (!ensureInitialAdmin(stage)) {
                        return;
                }

                // Show Login immediately — server starts in background
                showLoginScreen(stage);

                // Start Python Server in background (non-blocking)
                startPythonServer();

                // WhatsApp Bridge is started on demand from Settings to avoid slowing
                // down normal app startup for users who do not need it immediately.
        }

        private boolean initializeDatabase() {
                try {
                        org.example.MediManage.util.DatabaseUtil.initDB();
                        return true;
                } catch (Exception e) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Database Error");
                        alert.setHeaderText("Connection Failed");
                        alert.setContentText("Could not initialize the database. " + e.getMessage());
                        alert.showAndWait();
                        Platform.exit();
                        return false;
                }
        }

        private boolean ensureInitialAdmin(Stage owner) {
                try {
                        AdminBootstrapService bootstrapService = new AdminBootstrapService();
                        if (bootstrapService.requiresBootstrap()) {
                                bootstrapService.createDefaultAdminIfMissing();
                        }
                        return true;
                } catch (Exception e) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Admin Bootstrap Error");
                        alert.setHeaderText("Could not create the initial admin account");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                        Platform.exit();
                        return false;
                }
        }

        private void startPythonServer() {
                AppExecutors.runBackground(() -> {
                        try {
                                SidecarStartupAdvisor.Decision decision = evaluateAiEngineStartup();
                                if (decision.clearStaleMetadata()) {
                                        SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REUSE_EXISTING) {
                                        LOGGER.info(decision.message());
                                        return;
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REJECT_CONFLICT) {
                                        LOGGER.warning(decision.message());
                                        org.example.MediManage.util.ToastNotification
                                                        .warning("AI Engine port 5000 is occupied by another process.");
                                        return;
                                }
                                java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                                                .userNodeForPackage(org.example.MediManage.MediManageApplication.class);
                                java.io.File rawServerScript = new java.io.File(System.getProperty("user.dir"),
                                                "ai_engine/server/server.py");
                                java.io.File bundledExe = new java.io.File(System.getProperty("user.dir"),
                                                "ai_engine/python/python.exe");
                                java.io.File protectedScript = new java.io.File(System.getProperty("user.dir"),
                                                "ai_engine/dist/server/server.py");
                                boolean useProtectedBundle = !rawServerScript.exists()
                                                && bundledExe.exists()
                                                && protectedScript.exists();

                                String pythonExe = bundledExe.exists() ? bundledExe.getAbsolutePath() : "python";
                                String serverScript = useProtectedBundle
                                                ? protectedScript.getAbsolutePath()
                                                : rawServerScript.getAbsolutePath();
                                java.io.File protectedDistRoot = useProtectedBundle
                                                ? protectedScript.getParentFile().getParentFile()
                                                : null;
                                final String pythonExeFinal = pythonExe;
                                LOGGER.info("🚀 Starting AI Engine (" + serverScript + ")...");
                                ProcessBuilder pb = createPythonProcessBuilder(pythonExe, serverScript, protectedDistRoot);
                                pb.redirectErrorStream(true);
                                configureProtectedPythonPath(pb, protectedDistRoot);
                                pb.environment().put(LocalAdminTokenManager.ENV_NAME,
                                                LocalAdminTokenManager.getOrCreateToken());

                                pb.environment().put("MEDIMANAGE_DB_BACKEND", "sqlite");
                                pb.environment().put("MEDIMANAGE_DB_PATH",
                                                prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_DB_PATH,
                                                                System.getProperty("user.dir")
                                                                                + "/medimanage.db"));

                                pythonProcess = pb.start();
                                SidecarOwnershipMetadata.write(AI_SERVICE_NAME, AI_PORT, pythonProcess.pid());

                                // Add Shutdown Hook
                                Runtime.getRuntime().addShutdownHook(AppExecutors.newThread("jvm-shutdown-hook", () -> {
                                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                                LOGGER.info("🛑 JVM Shutdown Hook: Killing AI Engine...");
                                                pythonProcess.destroyForcibly();
                                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                        }
                                        if (mcpProcess != null && mcpProcess.isAlive()) {
                                                LOGGER.info("🛑 JVM Shutdown Hook: Killing MCP Server...");
                                                mcpProcess.destroyForcibly();
                                        }
                                }, false));

                                java.io.BufferedReader reader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(pythonProcess.getInputStream()));
                                String line;
                                boolean serverReady = false;
                                while ((line = reader.readLine()) != null) {
                                        LOGGER.info("[AI Engine]: " + line);
                                        if (!serverReady && line.contains("Running on")) {
                                                org.example.MediManage.util.ToastNotification
                                                                .success("AI Engine Ready");
                                                serverReady = true;
                                                startMcpServer(pythonExeFinal);
                                        }
                                }

                                int exitCode = pythonProcess.waitFor();
                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                if (!serverReady) {
                                        String failureMessage = "AI Engine exited before becoming ready (code " + exitCode + ").";
                                        LOGGER.warning("❌ " + failureMessage);
                                        org.example.MediManage.util.ToastNotification.error("AI Engine failed to start");
                                }
                        } catch (Exception e) {
                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                LOGGER.log(Level.SEVERE, "❌ Failed to start AI Engine: " + e.getMessage(), e);
                                org.example.MediManage.util.ToastNotification.error("AI Engine failed to start");
                        }
                });

        }

        private SidecarStartupAdvisor.Decision evaluateAiEngineStartup() {
                SidecarProbeResult probe = SidecarHttpProbe.probe(
                                URI.create("http://127.0.0.1:5000/health"),
                                AI_SERVICE_NAME,
                                LocalAdminTokenManager::applyHeader);
                return SidecarStartupAdvisor.decide("AI Engine",
                                probe,
                                SidecarOwnershipMetadata.read(AI_SERVICE_NAME));
        }

        private SidecarStartupAdvisor.Decision evaluateBridgeStartup() {
                SidecarProbeResult probe = SidecarHttpProbe.probe(
                                URI.create(WhatsAppBridgeConfig.statusUrl()),
                                WhatsAppBridgeConfig.SERVICE_NAME,
                                WhatsAppBridgeConfig::applyAdminHeader);
                return SidecarStartupAdvisor.decide("WhatsApp Bridge",
                                probe,
                                SidecarOwnershipMetadata.read(WhatsAppBridgeConfig.SERVICE_NAME));
        }

        /**
         * Start WhatsApp Bridge (Node.js server) on port 3000.
         * Mirrors the Python server lifecycle: auto-start, log forwarding.
         */
        public void startWhatsAppBridge() {
                AppExecutors.runBackground(() -> {
                        try {
                                SidecarStartupAdvisor.Decision decision = evaluateBridgeStartup();
                                if (decision.clearStaleMetadata()) {
                                        SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REUSE_EXISTING) {
                                        LOGGER.info(decision.message());
                                        return;
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REJECT_CONFLICT) {
                                        LOGGER.warning(decision.message());
                                        org.example.MediManage.util.ToastNotification.warning(
                                                        "WhatsApp Bridge port " + WhatsAppBridgeConfig.getPort()
                                                                        + " is occupied by another process.");
                                        return;
                                }

                                // Resolve Node.js executable (bundled or system)
                                String nodeExe = WhatsAppBridgeConfig.resolveNodeExe();

                                // Check if Node.js is available on this system
                                if (!WhatsAppBridgeConfig.isNodeAvailable()) {
                                        LOGGER.warning("⚠️ Node.js is not installed. WhatsApp Bridge requires Node.js. Skipping.");
                                        return;
                                }

                                java.io.File serverDir = WhatsAppBridgeConfig.resolveServerDir();
                                if (serverDir == null) {
                                        LOGGER.warning("⚠️ whatsapp-server/ directory not found. WhatsApp Bridge not started.");
                                        return;
                                }

                                // Check if node_modules exists, run npm install if missing
                                java.io.File nodeModules = new java.io.File(serverDir, "node_modules");
                                if (!nodeModules.isDirectory()) {
                                        LOGGER.info("📦 Installing WhatsApp Bridge dependencies (npm install)...");
                                        ProcessBuilder installPb = new ProcessBuilder("npm", "install");
                                        installPb.directory(serverDir);
                                        installPb.redirectErrorStream(true);
                                        Process installProc = installPb.start();
                                        // Consume install output
                                        try (java.io.BufferedReader r = new java.io.BufferedReader(
                                                        new java.io.InputStreamReader(installProc.getInputStream()))) {
                                                String l;
                                                while ((l = r.readLine()) != null) {
                                                        LOGGER.info("[npm install]: " + l);
                                                }
                                        }
                                        installProc.waitFor();
                                }

                                LOGGER.info("🟢 Starting WhatsApp Bridge (" + nodeExe + ", port "
                                                + WhatsAppBridgeConfig.getPort() + ")...");
                                ProcessBuilder pb = WhatsAppBridgeConfig.createStartProcessBuilder();
                                instance.whatsappBridgeProcess = pb.start();
                                SidecarOwnershipMetadata.write(WhatsAppBridgeConfig.SERVICE_NAME,
                                                WhatsAppBridgeConfig.getPort(), instance.whatsappBridgeProcess.pid());

                                // Consume output in background thread
                                java.io.BufferedReader reader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(instance.whatsappBridgeProcess.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                        LOGGER.info("[WhatsApp Bridge]: " + line);
                                }
                                SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                        } catch (Exception e) {
                                SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                                LOGGER.log(Level.WARNING, "⚠️ WhatsApp Bridge failed to start: " + e.getMessage(), e);
                        }
                });
        }


        /**
         * Restart the AI Engine with the currently active environment.
         * Called from SettingsController when user switches environment.
         */
        public void restartServer() {
                AppExecutors.runBackground(() -> {
                        LOGGER.info("🔄 Restarting AI Engine...");
                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                pythonProcess.destroyForcibly();
                                try {
                                        pythonProcess.waitFor();
                                } catch (InterruptedException ignored) {
                                        Thread.currentThread().interrupt();
                                }
                                LOGGER.info("🛑 Old AI Engine stopped.");
                        }
                        if (mcpProcess != null && mcpProcess.isAlive()) {
                                mcpProcess.destroyForcibly();
                                try {
                                        mcpProcess.waitFor();
                                } catch (InterruptedException ignored) {
                                        Thread.currentThread().interrupt();
                                }
                                LOGGER.info("🛑 Old MCP Server stopped.");
                        }
                        startPythonServer();
                });
        }

        /**
         * Start MCP Server on port 5001 as a sidecar process.
         * Uses the same Python environment as the AI Engine.
         */
        private void startMcpServer(String pythonExe) {
                AppExecutors.runBackground(() -> {
                        try {
                                // Check if port 5001 already in use
                                try (java.net.Socket ignored = new java.net.Socket("127.0.0.1", 5001)) {
                                        LOGGER.info("ℹ️ MCP Server already running on port 5001.");
                                        return;
                                } catch (Exception e) {
                                        // Port 5001 is free
                                }

                                LOGGER.info("🔌 Starting MCP Server (port 5001)...");
                                
                                java.io.File rawMcpScript = new java.io.File(System.getProperty("user.dir"),
                                                "ai_engine/server/mcp_server.py");
                                java.io.File protectedMcpScript = new java.io.File(System.getProperty("user.dir"),
                                                "ai_engine/dist/server/mcp_server.py");
                                boolean useProtectedBundle = !rawMcpScript.exists() && protectedMcpScript.exists();

                                String mcpScript = useProtectedBundle
                                                ? protectedMcpScript.getAbsolutePath()
                                                : rawMcpScript.getAbsolutePath();
                                java.io.File protectedDistRoot = useProtectedBundle
                                                ? protectedMcpScript.getParentFile().getParentFile()
                                                : null;
                                ProcessBuilder mcpPb = createPythonProcessBuilder(pythonExe, mcpScript, protectedDistRoot);
                                mcpPb.redirectErrorStream(true);
                                configureProtectedPythonPath(mcpPb, protectedDistRoot);
                                java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                                                .userNodeForPackage(org.example.MediManage.MediManageApplication.class);
                                mcpPb.environment().put(LocalAdminTokenManager.ENV_NAME,
                                                LocalAdminTokenManager.getOrCreateToken());
                                mcpPb.environment().put("MEDIMANAGE_DB_BACKEND", "sqlite");
                                mcpPb.environment().put("MEDIMANAGE_DB_PATH",
                                                prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_DB_PATH,
                                                                System.getProperty("user.dir")
                                                                                + "/medimanage.db"));
                                mcpProcess = mcpPb.start();

                                // Consume MCP output in background
                                java.io.BufferedReader mcpReader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(mcpProcess.getInputStream()));
                                String mcpLine;
                                while ((mcpLine = mcpReader.readLine()) != null) {
                                        LOGGER.info("[MCP Server]: " + mcpLine);
                                }
                        } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "⚠️ MCP Server failed to start: " + e.getMessage(), e);
                        }
                });
        }

        private void configureProtectedPythonPath(ProcessBuilder processBuilder, java.io.File distRoot) {
                if (processBuilder == null || distRoot == null || !distRoot.isDirectory()) {
                        return;
                }

                processBuilder.directory(distRoot);

                Map<String, String> env = processBuilder.environment();
                env.putIfAbsent("PYTHONIOENCODING", "utf-8");
        }

        private ProcessBuilder createPythonProcessBuilder(String pythonExe, String scriptPath, java.io.File distRoot) {
                if (distRoot == null || !distRoot.isDirectory()) {
                        return new ProcessBuilder(pythonExe, scriptPath);
                }

                String normalizedDistRoot = toPythonLiteralPath(distRoot.getAbsolutePath());
                String normalizedScriptPath = toPythonLiteralPath(scriptPath);
                String bootstrap = "import runpy, sys; sys.path.insert(0, '" + normalizedDistRoot
                                + "'); runpy.run_path('" + normalizedScriptPath + "', run_name='__main__')";

                return new ProcessBuilder(List.of(pythonExe, "-c", bootstrap));
        }

        private String toPythonLiteralPath(String path) {
                return path.replace("\\", "/").replace("'", "\\'");
        }

        private void showLoginScreen(Stage stage) {
                try {
                        FXMLLoader loader = new FXMLLoader(
                                        getClass().getResource("/org/example/MediManage/login-view.fxml"));
                        javafx.scene.Parent root = loader.load();

                        // Add drop shadow to the login card for popup effect
                        DropShadow shadow = new DropShadow();
                        shadow.setRadius(30);
                        shadow.setOffsetX(0);
                        shadow.setOffsetY(8);
                        shadow.setColor(Color.color(0, 0, 0, 0.6));
                        root.setEffect(shadow);

                        Scene scene = new Scene(root, 520, 500);
                        scene.setFill(Color.TRANSPARENT);
                        scene.getStylesheets().add(getClass().getResource("/org/example/MediManage/css/common.css")
                                        .toExternalForm());

                        // Create an undecorated popup stage
                        Stage loginStage = new Stage();
                        loginStage.initStyle(StageStyle.TRANSPARENT);
                        loginStage.initModality(Modality.APPLICATION_MODAL);
                        loginStage.setTitle("MediManage - Login");
                        try {
                                loginStage.getIcons().add(new Image(getClass().getResourceAsStream("/app_icon.png")));
                        } catch (Exception e) {
                                /* ignore */ }
                        loginStage.setScene(scene);
                        loginStage.setResizable(false);
                        loginStage.centerOnScreen();

                        // Pass the primary stage reference to the controller
                        LoginController controller = loader.getController();
                        controller.setPrimaryStage(stage);

                        loginStage.showAndWait();
                } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Failed to show login screen.", e);
                }
        }

        @Override
        public void stop() throws Exception {
                super.stop();
                if (pythonProcess != null) {
                        LOGGER.info("🛑 Stopping AI Engine...");
                        pythonProcess.destroyForcibly();
                        SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                } else {
                        // Try to kill via HTTP if process handle is missing
                        try {
                                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                .uri(URI.create("http://127.0.0.1:5000/shutdown"))
                                                .POST(HttpRequest.BodyPublishers.noBody());
                                LocalAdminTokenManager.applyHeader(requestBuilder);
                                HttpClient.newHttpClient().send(requestBuilder.build(),
                                                HttpResponse.BodyHandlers.discarding());
                                LOGGER.info("🛑 Sent shutdown signal to existing AI Engine.");
                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                        } catch (Exception ignored) {}
                }

                if (mcpProcess != null) {
                        LOGGER.info("🛑 Stopping MCP Server...");
                        mcpProcess.destroyForcibly();
                }

                if (whatsappBridgeProcess != null) {
                        LOGGER.info("🛑 Stopping WhatsApp Bridge...");
                        whatsappBridgeProcess.destroyForcibly();
                        SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                } else {
                        // Force shutdown of bridge via configured local port if process handle lost
                        try {
                                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                .uri(URI.create(WhatsAppBridgeConfig.shutdownUrl()))
                                                .POST(HttpRequest.BodyPublishers.noBody());
                                WhatsAppBridgeConfig.applyAdminHeader(requestBuilder);
                                HttpClient.newHttpClient().send(requestBuilder.build(),
                                                HttpResponse.BodyHandlers.discarding());
                                SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                        } catch (Exception ignored) {}
                }

                org.example.MediManage.service.DatabaseService.shutdown();
                AppExecutors.shutdown();
                Platform.exit();
                System.exit(0);
        }

        public static void main(String[] args) {
                launch();
        }
}
