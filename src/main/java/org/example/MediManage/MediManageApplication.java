package org.example.MediManage;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.application.Platform;

public class MediManageApplication extends Application {

        private static MediManageApplication instance;
        private volatile Process pythonProcess;
        private org.example.MediManage.service.ai.PythonEnvironmentManager envManager;
        private volatile StartupProgressController startupPopup;

        public static MediManageApplication getInstance() {
                return instance;
        }

        @Override
        public void start(Stage stage) throws Exception {
                instance = this;

                // Apply AtlantaFX Theme immediately
                Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());

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
                new Thread(() -> {
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

                                System.out.println("🚀 Starting AI Engine (server.py)...");
                                ProcessBuilder pb = new ProcessBuilder(pythonExe, "ai_engine/server.py");
                                pb.redirectErrorStream(true);

                                pythonProcess = pb.start();

                                // Add Shutdown Hook
                                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                                System.out.println("🛑 JVM Shutdown Hook: Killing AI Engine...");
                                                pythonProcess.destroyForcibly();
                                        }
                                }));

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
                                                        // Auto-close after 2 seconds
                                                        new Thread(() -> {
                                                                try {
                                                                        Thread.sleep(2000);
                                                                } catch (InterruptedException ignored) {
                                                                }
                                                                closeStartupPopup();
                                                        }).start();
                                                }
                                                popupClosed = true;
                                        }
                                }
                        } catch (Exception e) {
                                System.err.println("❌ Failed to start AI Engine: " + e.getMessage());
                                e.printStackTrace();
                                if (startupPopup != null && !startupPopup.isClosed()) {
                                        startupPopup.appendLog("❌ Error: " + e.getMessage());
                                        startupPopup.setStatus("❌ Failed to start AI Engine");
                                }
                        }
                }).start();
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
                new Thread(() -> {
                        System.out.println("🔄 Restarting AI Engine...");
                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                pythonProcess.destroyForcibly();
                                try {
                                        pythonProcess.waitFor();
                                } catch (InterruptedException ignored) {
                                }
                                System.out.println("🛑 Old AI Engine stopped.");
                        }
                        startPythonServer();
                }).start();
        }

        private void showLoginScreen(Stage stage) {
                try {
                        FXMLLoader loader = new FXMLLoader(
                                        getClass().getResource("/org/example/MediManage/login-view.fxml"));
                        Scene scene = new Scene(loader.load(), 420, 520);
                        stage.setTitle("Medical Billing System - Login");
                        try {
                                stage.getIcons().add(new Image(getClass().getResourceAsStream("/app_icon.png")));
                        } catch (Exception e) {
                                /* ignore */ }
                        stage.setScene(scene);
                        stage.show();
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
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(
                                                "http://127.0.0.1:5000/shutdown").openConnection();
                                conn.setRequestMethod("POST");
                                conn.getInputStream();
                                System.out.println("🛑 Sent shutdown signal to existing AI Engine.");
                        } catch (Exception e) {
                                // Ignore, server probably not running
                        }
                }
                org.example.MediManage.service.DatabaseService.shutdown();
                Platform.exit();
                System.exit(0);
        }

        public static void main(String[] args) {
                launch();
        }
}
