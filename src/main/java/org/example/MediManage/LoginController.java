package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.MediManage.dao.UserDAO;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;

import java.util.Objects;

public class LoginController {

    @FXML
    private TextField username;

    @FXML
    private PasswordField password;

    @FXML
    private Label message;

    private UserDAO userDAO = new UserDAO();

    @FXML
    private ComboBox<UserRole> roleSelector;

    @FXML
    private void initialize() {
        roleSelector.getItems().setAll(UserRole.values());
    }

    @FXML
    private void handleLogin() {
        String user = username.getText();
        String pass = password.getText();
        UserRole selectedRole = roleSelector.getValue();

        if (selectedRole == null) {
            message.setText("Please select a Role ⚠️");
            return;
        }

        try {
            User authenticatedUser = userDAO.authenticate(user, pass);

            if (authenticatedUser != null) {
                // Validate Role
                if (authenticatedUser.getRole() != selectedRole) {
                    message.setText("Role Mismatch! You are " + authenticatedUser.getRole() + " ❌");
                    return;
                }

                UserSession.getInstance().login(authenticatedUser);
                message.setText("Login Successful ✅");

                // Switch to Main Shell
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("main-shell-view.fxml"));
                    Stage stage = (Stage) username.getScene().getWindow(); // current stage
                    stage.setScene(new Scene(loader.load(), 900, 600)); // Increased height specific for shell
                    stage.setTitle("MediManage - " + authenticatedUser.getRole());
                    stage.show();
                } catch (Exception e) {
                    e.printStackTrace();
                    message.setText("Error loading shell: " + e.getMessage());
                }

            } else {
                message.setText("Invalid Username or Password ❌");
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            // Show Alert
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Connection Error");
            alert.setContentText("Details: " + e.getMessage());
            alert.showAndWait();

            message.setText("DB Conn Error: " + e.getMessage());
        }
    }

    private void openDashboard() {
        try {
            Parent root = FXMLLoader.load(
                    Objects.requireNonNull(getClass().getResource(
                            "/org/example/MediManage/dashboard-view.fxml")));

            Stage stage = (Stage) username.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Dashboard");
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
