package org.example.MediManage;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.scene.control.Alert;
import javafx.application.Platform;
import javafx.scene.text.Font;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.util.AppExecutors;

import java.util.concurrent.TimeUnit;

public class MediManageApplication extends Application {

        private static MediManageApplication instance;
        private volatile Process pythonProcess;
        private volatile Process mcpProcess;
        private org.example.MediManage.service.ai.PythonEnvironmentManager envManager;
        private volatile StartupProgressController startupPopup;

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

                // Show Login immediately — server starts in background
                showLoginScreen(stage);

                // Start Python Server in background (non-blocking)
                startPythonServer();

                // Async Database Init
                org.example.MediManage.service.DatabaseService.initializeAsync(() -> {
                        System.out.println("✅ Background DB Init Complete");
                }, () -> {
                        Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Database Error");
                                alert.setHeaderText("Connection Failed");
                                alert.setContentText("Could not connect to the database. Please check logs.");
                                alert.showAndWait();
                        });
                });
        }

        private void startPythonServer() {
                AppExecutors.runBackground(() -> {
                        try {
                                // Check if port 5000 is already in use (Server running)
                                try (java.net.Socket ignored = new java.net.Socket("127.0.0.1", 5000)) {
                                        System.out.println("ℹ️ AI Engine already running on port 5000.");
                                        return;
                                } catch (Exception e) {
                                        // Port 5000 is free, start server
                                }

                                // Get environment manager
                                envManager = org.example.MediManage.service.ai.AIServiceProvider.get().getEnvManager();

                                // Read user preference for active environment
                                java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                                                .userNodeForPackage(org.example.MediManage.SettingsController.class);
                                String envPref = prefs.get("active_python_env", "cpu");

                                // Auto-detect best GPU environment when hardware is set to "Auto"
                                String aiHardware = prefs.get("ai_hardware", "Auto");
                                if ("Auto".equals(aiHardware)) {
                                        String detected = envManager.autoDetectBestEnv();
                                        if (!detected.equals(envPref)) {
                                                System.out.println("🎯 Auto-switching environment: "
                                                                + envPref + " → " + detected);
                                                envPref = detected;
                                                prefs.put("active_python_env", envPref);
                                        }
                                }

                                envManager.setActiveEnvironment(envPref);

                                // Check if environment needs setup
                                boolean needsSetup = !envManager.isEnvReady(envPref);

                                if (needsSetup) {
                                        // Show progress popup only when setup is actually required
                                        System.out.println("📦 Environment '" + envPref
                                                        + "' needs setup. Showing progress popup...");
                                        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(
                                                        1);
                                        Platform.runLater(() -> {
                                                startupPopup = StartupProgressController.show();
                                                if (startupPopup != null) {
                                                        startupPopup.setStatus("⚙️ Setting up "
                                                                        + envManager.getActiveLabel() + "...");
                                                        startupPopup.appendLog(
                                                                        "🔧 First-time setup — installing Python environment.");
                                                        startupPopup.appendLog(
                                                                        "⏳ This may take a few minutes. Sorry for the inconvenience!");
                                                        startupPopup.appendLog("");
                                                }
                                                latch.countDown();
                                        });
                                        latch.await(); // Wait for popup to be created

                                        // Connect log streaming to popup
                                        envManager.setLogCallback(msg -> {
                                                if (startupPopup != null && !startupPopup.isClosed()) {
                                                        startupPopup.appendLog(msg);
                                                }
                                        });
                                } else {
                                        System.out.println("✅ Environment '" + envPref + "' already ready.");
                                }

                                // Ensure environment (will be fast if already set up)
                                String pythonExe;
                                try {
                                        pythonExe = envManager.ensureEnvironment();
                                        System.out.println("🐍 Using Python: " + pythonExe);
                                } catch (InterruptedException cancelEx) {
                                        System.out.println("🛑 Python environment setup was cancelled.");
                                        closeStartupPopup();
                                        return;
                                } catch (Exception envEx) {
                                        System.err.println(
                                                        "⚠️ Could not setup bundled Python env: " + envEx.getMessage());
                                        if (startupPopup != null)
                                                startupPopup.appendLog("⚠️ Falling back to system Python...");
                                        pythonExe = "python";
                                }

                                // Update popup status
                                if (startupPopup != null && !startupPopup.isClosed()) {
                                        startupPopup.setStatus("🚀 Starting AI Engine...");
                                }

                                final String pythonExeFinal = pythonExe;
                                System.out.println("🚀 Starting AI Engine (server.py)...");
                                ProcessBuilder pb = new ProcessBuilder(pythonExe, "ai_engine/server.py");
                                pb.redirectErrorStream(true);
                                pb.environment().put(LocalAdminTokenManager.ENV_NAME,
                                                LocalAdminTokenManager.getOrCreateToken());

                                // Pass HuggingFace token for faster model downloads
                                String hfToken = prefs.get("hf_token", "").trim();
                                if (!hfToken.isEmpty()) {
                                        pb.environment().put("HF_TOKEN", hfToken);
                                        System.out.println(
                                                        "🔑 HF_TOKEN provided — authenticated HuggingFace downloads enabled.");
                                }

                                // Pass database config to Python AI engine
                                String dbBackend = prefs.get(
                                                org.example.MediManage.config.DatabaseConfig.PREF_DB_BACKEND,
                                                "sqlite");
                                pb.environment().put("MEDIMANAGE_DB_BACKEND", dbBackend);
                                if ("postgresql".equals(dbBackend)) {
                                        pb.environment().put("MEDIMANAGE_PG_HOST",
                                                        prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_PG_HOST,
                                                                        "localhost"));
                                        pb.environment().put("MEDIMANAGE_PG_PORT",
                                                        prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_PG_PORT,
                                                                        "5432"));
                                        pb.environment().put("MEDIMANAGE_PG_DATABASE",
                                                        prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_PG_DATABASE,
                                                                        "medimanage"));
                                        pb.environment().put("MEDIMANAGE_PG_USER",
                                                        prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_PG_USER,
                                                                        "postgres"));
                                        pb.environment().put("MEDIMANAGE_PG_PASSWORD",
                                                        prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_PG_PASSWORD,
                                                                        ""));
                                } else {
                                        pb.environment().put("MEDIMANAGE_DB_PATH",
                                                        prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_DB_PATH,
                                                                        System.getProperty("user.dir")
                                                                                        + "/medimanage.db"));
                                }

                                pythonProcess = pb.start();

                                // Add Shutdown Hook
                                Runtime.getRuntime().addShutdownHook(AppExecutors.newThread("jvm-shutdown-hook", () -> {
                                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                                System.out.println("🛑 JVM Shutdown Hook: Killing AI Engine...");
                                                pythonProcess.destroyForcibly();
                                        }
                                        if (mcpProcess != null && mcpProcess.isAlive()) {
                                                System.out.println("🛑 JVM Shutdown Hook: Killing MCP Server...");
                                                mcpProcess.destroyForcibly();
                                        }
                                }, false));

                                // Consume output — close popup once server is ready
                                java.io.BufferedReader reader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(pythonProcess.getInputStream()));
                                String line;
                                boolean popupClosed = false;
                                while ((line = reader.readLine()) != null) {
                                        System.out.println("[AI Engine]: " + line);
                                        if (startupPopup != null && !startupPopup.isClosed()) {
                                                startupPopup.appendLog("[AI Engine]: " + line);
                                        }
                                        // Close popup once Flask reports "Running on"
                                        if (!popupClosed && line.contains("Running on")) {
                                                if (startupPopup != null && !startupPopup.isClosed()) {
                                                        startupPopup.setStatus("✅ AI Engine Ready!");
                                                        startupPopup.setProgress(1.0);
                                                        startupPopup.appendLog("");
                                                        startupPopup.appendLog(
                                                                        "✅ Setup complete! This window will close shortly.");
                                                }
                                                org.example.MediManage.util.ToastNotification
                                                                .success("AI Engine Ready");
                                                // Auto-close after 2 seconds
                                                AppExecutors.schedule(
                                                                () -> Platform.runLater(
                                                                                this::closeStartupPopup),
                                                                2,
                                                                TimeUnit.SECONDS);
                                                popupClosed = true;

                                                // Start MCP Server on port 5001 alongside Flask
                                                startMcpServer(pythonExeFinal);
                                        }
                                }
                        } catch (Exception e) {
                                System.err.println("❌ Failed to start AI Engine: " + e.getMessage());
                                e.printStackTrace();
                                if (startupPopup != null && !startupPopup.isClosed()) {
                                        startupPopup.appendLog("❌ Error: " + e.getMessage());
                                        startupPopup.setStatus("❌ Failed to start AI Engine");
                                }
                                org.example.MediManage.util.ToastNotification.error("AI Engine failed to start");
                        }
                });

        }

        private void closeStartupPopup() {
                if (startupPopup != null) {
                        startupPopup.close();
                        startupPopup = null;
                }
        }

        /**
         * Restart the AI Engine with the currently active environment.
         * Called from SettingsController when user switches environment.
         */
        public void restartServer() {
                AppExecutors.runBackground(() -> {
                        System.out.println("🔄 Restarting AI Engine...");
                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                pythonProcess.destroyForcibly();
                                try {
                                        pythonProcess.waitFor();
                                } catch (InterruptedException ignored) {
                                        Thread.currentThread().interrupt();
                                }
                                System.out.println("🛑 Old AI Engine stopped.");
                        }
                        if (mcpProcess != null && mcpProcess.isAlive()) {
                                mcpProcess.destroyForcibly();
                                try {
                                        mcpProcess.waitFor();
                                } catch (InterruptedException ignored) {
                                        Thread.currentThread().interrupt();
                                }
                                System.out.println("🛑 Old MCP Server stopped.");
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
                                        System.out.println("ℹ️ MCP Server already running on port 5001.");
                                        return;
                                } catch (Exception e) {
                                        // Port 5001 is free
                                }

                                System.out.println("🔌 Starting MCP Server (port 5001)...");
                                ProcessBuilder mcpPb = new ProcessBuilder(pythonExe, "ai_engine/mcp_server.py");
                                mcpPb.redirectErrorStream(true);
                                mcpProcess = mcpPb.start();

                                // Consume MCP output in background
                                java.io.BufferedReader mcpReader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(mcpProcess.getInputStream()));
                                String mcpLine;
                                while ((mcpLine = mcpReader.readLine()) != null) {
                                        System.out.println("[MCP Server]: " + mcpLine);
                                }
                        } catch (Exception e) {
                                System.err.println("⚠️ MCP Server failed to start: " + e.getMessage());
                        }
                });
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
                        e.printStackTrace();
                }
        }

        @Override
        public void stop() throws Exception {
                super.stop();
                // Close startup popup if still open
                closeStartupPopup();
                // Cancel any in-progress env setup
                if (envManager != null) {
                        envManager.cancel();
                }
                if (pythonProcess != null) {
                        System.out.println("🛑 Stopping AI Engine...");
                        pythonProcess.destroyForcibly();
                } else {
                        // Try to kill via HTTP if process handle is missing
                        try {
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI
                                                .create("http://127.0.0.1:5000/shutdown")
                                                .toURL()
                                                .openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty(LocalAdminTokenManager.HEADER_NAME,
                                                LocalAdminTokenManager.getOrCreateToken());
                                conn.getInputStream();
                                System.out.println("🛑 Sent shutdown signal to existing AI Engine.");
                        } catch (Exception e) {
                                // Ignore, server probably not running
                        }
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
