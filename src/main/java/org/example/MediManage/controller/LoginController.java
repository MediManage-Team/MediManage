package org.example.MediManage.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.MediManage.dao.UserDAO;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.UserSession;



public class LoginController {

    @FXML
    private TextField username;

    @FXML
    private PasswordField password;

    @FXML
    private Label message;

    private UserDAO userDAO = new UserDAO();
    private Stage primaryStage;

    @FXML
    private ComboBox<UserRole> roleSelector;

    /** Called by MediManageApplication to pass the primary stage reference. */
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

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

                // Close the login popup and show main shell on the primary stage
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/MediManage/main-shell-view.fxml"));
                    Stage currentStage = (Stage) username.getScene().getWindow();

                    // Determine whether login is a popup or on the primary stage
                    if (primaryStage != null && currentStage != primaryStage) {
                        // Login is a popup — close it, load shell on primaryStage
                        currentStage.close();
                        primaryStage.setScene(new Scene(loader.load()));
                        primaryStage.setTitle("MediManage - " + authenticatedUser.getRole());
                        primaryStage.setMaximized(true);
                        primaryStage.show();
                    } else {
                        // Login is directly on the primary stage (after logout)
                        Stage target = (primaryStage != null) ? primaryStage : currentStage;
                        target.setScene(new Scene(loader.load()));
                        target.setTitle("MediManage - " + authenticatedUser.getRole());
                        target.setMaximized(true);
                        target.show();
                    }
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

        AppExecutors.background().execute(loginTask);
    }

    private void reenableUI() {
        roleSelector.setDisable(false);
        username.setDisable(false);
        password.setDisable(false);
    }


}
