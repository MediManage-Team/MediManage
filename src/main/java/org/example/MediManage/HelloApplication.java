package org.example.MediManage;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // Initialize DB once
        DBUtil.initDB();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/org/example/MediManage/login-view.fxml"
                )
        );

        Scene scene = new Scene(loader.load(), 420, 520);

        // Correct CSS path
        scene.getStylesheets().add(
                getClass().getResource(
                        "/org/example/MediManage/style.css"
                ).toExternalForm()
        );

        stage.setTitle("Medical Billing System - Login");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
