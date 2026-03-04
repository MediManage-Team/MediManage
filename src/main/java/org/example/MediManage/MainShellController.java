package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.MediManage.model.User;
import org.example.MediManage.util.AnimationUtils;
import org.example.MediManage.util.SidebarManager;
import org.example.MediManage.util.ToastNotification;
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

    private StackPane toastOverlay;

    @FXML
    public void initialize() {
        User currentUser = UserSession.getInstance().getUser();
        if (currentUser == null) {
            System.err.println("No user logged in. Redirecting to login...");
            handleLogout();
            return;
        }

        userInfoLabel.setText("User: " + currentUser.getUsername() + " (" + currentUser.getRole() + ")");

        // ── Install universal toast overlay ──
        toastOverlay = new StackPane();
        toastOverlay.setPickOnBounds(false);
        toastOverlay.setMouseTransparent(false);
        ToastNotification.install(toastOverlay);

        SidebarManager.generateSidebar(
                sidebar,
                currentUser.getRole(),
                this::handleLogout,
                this);

        // Apply Theme
        applyTheme(currentUser.getRole());

        // Animate sidebar entrance
        AnimationUtils.slideInFromLeft(sidebar, 350, 30);

        // Load default view
        // Load home view based on role
        String homeView = "dashboard-view";
        switch (currentUser.getRole()) {
            case CASHIER:
                homeView = "billing-view";
                break;
            case STAFF:
                homeView = "billing-view";
                break;
            default:
                homeView = "dashboard-view";
                break;
        }
        switchView(homeView);

        // Welcome toast
        ToastNotification.success("Welcome, " + currentUser.getUsername() + "!");
    }

    @Override
    public void switchView(String viewName) {
        loadView(viewName);
    }

    private void loadView(String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(viewName + ".fxml"));
            Parent view = loader.load();

            // Wrap content + toast overlay in a StackPane
            StackPane contentWithOverlay = new StackPane(view, toastOverlay);
            mainLayout.setCenter(contentWithOverlay);
            AnimationUtils.fadeIn(view, 250);
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
        try {
            // Load unified dark theme (includes all role accent overrides)
            String commonCssUrl = Objects.requireNonNull(getClass().getResource("css/common.css")).toExternalForm();
            mainLayout.getStylesheets().add(commonCssUrl);

            // Apply role-specific style class for accent color overrides
            switch (role) {
                case CASHIER:
                    mainLayout.getStyleClass().add("role-cashier");
                    break;
                case PHARMACIST:
                    mainLayout.getStyleClass().add("role-pharmacist");
                    break;
                default:
                    // Admin/Manager use default cyan accent — no extra class needed
                    break;
            }
        } catch (Exception e) {
            System.err.println("Failed to load theme: " + e.getMessage());
        }
    }

    private void handleLogout() {
        UserSession.getInstance().logout();
        try {
            Stage primaryStage = (Stage) mainLayout.getScene().getWindow();

            // Load login view
            FXMLLoader loader = new FXMLLoader(
                    Objects.requireNonNull(getClass().getResource("login-view.fxml")));
            Parent loginRoot = loader.load();

            // Drop shadow for the login card
            javafx.scene.effect.DropShadow shadow = new javafx.scene.effect.DropShadow();
            shadow.setRadius(30);
            shadow.setOffsetY(8);
            shadow.setColor(javafx.scene.paint.Color.color(0, 0, 0, 0.6));
            loginRoot.setEffect(shadow);

            Scene loginScene = new Scene(loginRoot, 520, 500);
            loginScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            loginScene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("css/common.css")).toExternalForm());

            // Create a NEW transparent stage (no title bar, no decorations)
            Stage loginStage = new Stage();
            loginStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            loginStage.setTitle("MediManage - Login");
            try {
                loginStage.getIcons().add(new javafx.scene.image.Image(
                        getClass().getResourceAsStream("/app_icon.png")));
            } catch (Exception ignored) {
            }
            loginStage.setScene(loginScene);
            loginStage.setResizable(false);
            loginStage.centerOnScreen();

            // Pass primary stage so LoginController can re-show it
            LoginController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);

            // HIDE the primary stage, show the login stage
            primaryStage.hide();
            loginStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
