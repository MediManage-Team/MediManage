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
        // Filter out STAFF as per user request
        java.util.List<UserRole> roles = new java.util.ArrayList<>();
        for (UserRole r : UserRole.values()) {
            if (r != UserRole.STAFF) {
                roles.add(r);
            }
        }
        roleSelector.getItems().setAll(roles);
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

        message.setText("Logging in...");
        // Disable UI to prevent double submission
        roleSelector.setDisable(true);
        username.setDisable(true);
        password.setDisable(true);

        javafx.concurrent.Task<User> loginTask = new javafx.concurrent.Task<>() {
            @Override
            protected User call() throws Exception {
                return userDAO.authenticate(user, pass);
            }
        };

        loginTask.setOnSucceeded(e -> {
            User authenticatedUser = loginTask.getValue(); // Safe to reuse var name here? No, let's use the return
                                                           // value
            reenableUI();

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
                } catch (Exception ex) {
                    ex.printStackTrace();
                    message.setText("Error loading shell: " + ex.getMessage());
                }

            } else {
                message.setText("Invalid Username or Password ❌");
            }
        });

        loginTask.setOnFailed(e -> {
            reenableUI();
            Throwable ex = loginTask.getException();
            ex.printStackTrace();
            // Show Alert
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Connection Error");
            alert.setContentText("Details: " + ex.getMessage());
            alert.showAndWait();

            message.setText("DB Conn Error: " + ex.getMessage());
        });

        new Thread(loginTask).start();
    }

    private void reenableUI() {
        roleSelector.setDisable(false);
        username.setDisable(false);
        password.setDisable(false);
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
