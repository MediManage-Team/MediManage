package org.example.MediManage;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.application.Platform;

public class MediManageApplication extends Application {

        private Process pythonProcess;

        @Override
        public void start(Stage stage) throws Exception {
                // Start Python Server
                startPythonServer();

                // Apply AtlantaFX Theme immediately
                Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());

                // Show Splash or Login immediately while connecting in background
                showLoginScreen(stage);

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

                                System.out.println("🚀 Starting AI Engine (server.py)...");
                                ProcessBuilder pb = new ProcessBuilder("python", "ai_engine/server.py");
                                pb.redirectErrorStream(true);

                                pythonProcess = pb.start();

                                // Add Shutdown Hook to ensure process is killed even if stop() is missed
                                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                                System.out.println("🛑 JVM Shutdown Hook: Killing AI Engine...");
                                                pythonProcess.destroyForcibly();
                                        }
                                }));

                                // Consume output to prevent blocking
                                java.io.BufferedReader reader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(pythonProcess.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                        System.out.println("[AI Engine]: " + line);
                                }
                        } catch (Exception e) {
                                System.err.println("❌ Failed to start AI Engine: " + e.getMessage());
                                e.printStackTrace();
                        }
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
                if (pythonProcess != null) {
                        System.out.println("🛑 Stopping AI Engine...");
                        pythonProcess.destroyForcibly();
                } else {
                        // Try to kill via HTTP if process handle is missing (Zombie case)
                        try {
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(
                                                "http://127.0.0.1:5000/shutdown").openConnection();
                                conn.setRequestMethod("POST");
                                conn.getInputStream(); // Fire request
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
