package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.MediManage.model.User;
import org.example.MediManage.util.SidebarManager;
import org.example.MediManage.util.UserSession;
import org.example.MediManage.util.ViewSwitcher;

import java.io.IOException;
import java.util.Objects;

public class MainShellController implements ViewSwitcher {

    @FXML
    private BorderPane mainLayout;

    @FXML
    private VBox sidebar;

    @FXML
    private Label userInfoLabel;

    @FXML
    public void initialize() {
        User currentUser = UserSession.getInstance().getUser();
        if (currentUser == null) {
            System.err.println("No user logged in. Redirecting to login...");
            handleLogout();
            return;
        }

        userInfoLabel.setText("User: " + currentUser.getUsername() + " (" + currentUser.getRole() + ")");

        SidebarManager.generateSidebar(
                sidebar,
                currentUser.getRole(),
                this::handleLogout,
                this);

        // Apply Theme
        applyTheme(currentUser.getRole());

        // Load default view
        // Load home view based on role
        String homeView = "dashboard-view";
        switch (currentUser.getRole()) {
            case CASHIER:
                homeView = "billing-view";
                break;
            case PHARMACIST:
                homeView = "medicine-search-view";
                break;
            default:
                homeView = "dashboard-view";
                break;
        }
        switchView(homeView);
    }

    @Override
    public void switchView(String viewName) {
        loadView(viewName);
    }

    private void loadView(String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(viewName + ".fxml"));
            Parent view = loader.load();
            mainLayout.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Could not load view: " + viewName);
            showError("Load Error", "Could not load view: " + viewName);
        } catch (SecurityException e) {
            showError("Access Denied", e.getMessage());
        } catch (RuntimeException e) {
            // Unpack generic runtime wrapping if needed, though usually just show message
            if (e.getCause() instanceof SecurityException) {
                showError("Access Denied", e.getCause().getMessage());
            } else {
                e.printStackTrace();
                // showError("Application Error", e.getMessage()); // Optional: might be too
                // noisy
            }
        }
    }

    private void showError(String title, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    private void applyTheme(org.example.MediManage.model.UserRole role) {
        String cssInfo = "";
        try {
            String cssPath = "";
            switch (role) {
                case ADMIN:
                    cssPath = "css/admin.css";
                    break;
                case CASHIER:
                    cssPath = "css/cashier.css";
                    break;
                case PHARMACIST:
                    cssPath = "css/pharmacist.css";
                    break;
                default:
                    return; // No specific theme
            }

            if (!cssPath.isEmpty()) {
                String cssUrl = Objects.requireNonNull(getClass().getResource(cssPath)).toExternalForm();
                mainLayout.getStylesheets().add(cssUrl);
            }
        } catch (Exception e) {
            System.err.println("Failed to load theme: " + e.getMessage());
        }
    }

    private void handleLogout() {
        UserSession.getInstance().logout();
        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("login-view.fxml")));
            Stage stage = (Stage) mainLayout.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("MediManage - Login");
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
