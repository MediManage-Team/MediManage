package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;

public class LoginController {

    @FXML
    private TextField username;

    @FXML
    private PasswordField password;

    @FXML
    private Label message;

    @FXML
    private void initialize() {
        DBUtil.initDB();
    }

    @FXML
    private void handleLogin() {
        String user = username.getText();
        String pass = password.getText();

        if (authenticate(user, pass)) {
            message.setText("Login Successful ✅");

            // Switch to dashboard
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard-view.fxml"));
                Stage stage = (Stage) username.getScene().getWindow(); // current stage
                stage.setScene(new Scene(loader.load(), 900, 500));
                stage.setTitle("Medical Store Dashboard");
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            message.setText("Invalid Username or Password ❌");
        }
    }


    private boolean authenticate(String user, String pass) {
        String sql = "SELECT * FROM users WHERE username=? AND password=?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user);
            ps.setString(2, pass);

            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void openDashboard() {
        try {
            Parent root = FXMLLoader.load(
                    Objects.requireNonNull(getClass().getResource(
                            "/org/example/MediManage/dashboard-view.fxml"
                    ))
            );

            Stage stage = (Stage) username.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Dashboard");
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
