package org.example.MediManage;

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
import org.example.MediManage.util.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the login screen.
 * Handles user authentication with rate limiting to prevent brute force
 * attacks.
 */
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    // Rate limiting configuration
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;
    private static final Map<String, LoginAttemptInfo> loginAttempts = new HashMap<>();

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
        logger.debug("Login controller initialized");
    }

    @FXML
    private void handleLogin() {
        String user = username.getText();
        String pass = password.getText();
        UserRole selectedRole = roleSelector.getValue();

        // Validation
        if (user == null || user.trim().isEmpty()) {
            message.setText("Please enter username ⚠️");
            return;
        }

        if (pass == null || pass.trim().isEmpty()) {
            message.setText("Please enter password ⚠️");
            return;
        }

        if (selectedRole == null) {
            message.setText("Please select a Role ⚠️");
            return;
        }

        // Check if account is locked
        if (isAccountLocked(user)) {
            long remainingMinutes = getRemainingLockoutMinutes(user);
            message.setText(String.format("Account locked. Try again in %d minutes ⚠️", remainingMinutes));
            logger.warn("Login attempt for locked account: {}", user);
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
            User authenticatedUser = loginTask.getValue();
            reenableUI();

            if (authenticatedUser != null) {
                // Validate Role
                if (authenticatedUser.getRole() != selectedRole) {
                    message.setText("Role Mismatch! You are " + authenticatedUser.getRole() + " ❌");
                    recordFailedAttempt(user);
                    logger.warn("Role mismatch for user {}: expected {}, actual {}",
                            user, selectedRole, authenticatedUser.getRole());
                    return;
                }

                // Successful login - clear attempts
                clearLoginAttempts(user);
                UserSession.getInstance().login(authenticatedUser);
                message.setText("Login Successful ✅");
                logger.info("User '{}' logged in successfully with role {}", user, selectedRole);

                // Switch to Main Shell
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("main-shell-view.fxml"));
                    Stage stage = (Stage) username.getScene().getWindow();
                    stage.setScene(new Scene(loader.load(), 900, 600));
                    stage.setTitle("MediManage - " + authenticatedUser.getRole());
                    stage.show();
                } catch (Exception ex) {
                    logger.error("Error loading main shell", ex);
                    message.setText("Error loading application: " + ex.getMessage());
                }

            } else {
                recordFailedAttempt(user);
                int remainingAttempts = getRemainingAttempts(user);

                if (remainingAttempts > 0) {
                    message.setText(String.format("Invalid credentials ❌ (%d attempts remaining)", remainingAttempts));
                    logger.warn("Failed login attempt for user '{}' - {} attempts remaining", user, remainingAttempts);
                } else {
                    message.setText("Account locked due to too many failed attempts ⚠️");
                    logger.warn("Account locked for user '{}' due to excessive failed login attempts", user);
                }
            }
        });

        loginTask.setOnFailed(e -> {
            reenableUI();
            Throwable ex = loginTask.getException();
            logger.error("Login error for user '{}'", user, ex);

            // Show Alert
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Connection Error");
            alert.setContentText("Details: " + ex.getMessage());
            alert.showAndWait();

            message.setText("System Error: " + ex.getMessage());
        });

        new Thread(loginTask).start();
    }

    private void reenableUI() {
        roleSelector.setDisable(false);
        username.setDisable(false);
        password.setDisable(false);
    }

    /**
     * Checks if an account is currently locked.
     */
    private boolean isAccountLocked(String username) {
        LoginAttemptInfo info = loginAttempts.get(username);
        if (info == null) {
            return false;
        }

        if (info.attempts >= MAX_LOGIN_ATTEMPTS) {
            LocalDateTime lockoutEnd = info.lastAttempt.plusMinutes(LOCKOUT_DURATION_MINUTES);
            if (LocalDateTime.now().isBefore(lockoutEnd)) {
                return true;
            } else {
                // Lockout expired, clear attempts
                clearLoginAttempts(username);
                return false;
            }
        }
        return false;
    }

    /**
     * Gets remaining lockout minutes.
     */
    private long getRemainingLockoutMinutes(String username) {
        LoginAttemptInfo info = loginAttempts.get(username);
        if (info == null) {
            return 0;
        }
        LocalDateTime lockoutEnd = info.lastAttempt.plusMinutes(LOCKOUT_DURATION_MINUTES);
        long minutesRemaining = java.time.Duration.between(LocalDateTime.now(), lockoutEnd).toMinutes();
        return Math.max(1, minutesRemaining);
    }

    /**
     * Records a failed login attempt.
     */
    private void recordFailedAttempt(String username) {
        LoginAttemptInfo info = loginAttempts.getOrDefault(username, new LoginAttemptInfo());
        info.attempts++;
        info.lastAttempt = LocalDateTime.now();
        loginAttempts.put(username, info);
    }

    /**
     * Gets remaining login attempts before lockout.
     */
    private int getRemainingAttempts(String username) {
        LoginAttemptInfo info = loginAttempts.get(username);
        if (info == null) {
            return MAX_LOGIN_ATTEMPTS;
        }
        return Math.max(0, MAX_LOGIN_ATTEMPTS - info.attempts);
    }

    /**
     * Clears login attempts for a user (called on successful login).
     */
    private void clearLoginAttempts(String username) {
        loginAttempts.remove(username);
    }

    /**
     * Stores login attempt information for rate limiting.
     */
    private static class LoginAttemptInfo {
        int attempts = 0;
        LocalDateTime lastAttempt = LocalDateTime.now();
    }
}
