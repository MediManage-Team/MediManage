package org.example.MediManage;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import javafx.scene.control.Alert;
import javafx.application.Platform;

public class HelloApplication extends Application {

        @Override
        public void start(Stage stage) throws Exception {
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
                org.example.MediManage.service.DatabaseService.shutdown();
                Platform.exit();
                System.exit(0); // Force kill JVM in case of lingering threads
        }

        public static void main(String[] args) {
                launch();
        }
}
