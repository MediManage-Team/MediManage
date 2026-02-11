package org.example.MediManage.util;

import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Utility class for showing toast notifications.
 * Provides non-blocking, auto-dismissing notifications for user feedback.
 */
public class ToastUtil {

    /**
     * Toast notification types.
     */
    public enum ToastType {
        SUCCESS("fas-check-circle", "-fx-background-color: #27ae60;"),
        ERROR("fas-times-circle", "-fx-background-color: #e74c3c;"),
        WARNING("fas-exclamation-triangle", "-fx-background-color: #f39c12;"),
        INFO("fas-info-circle", "-fx-background-color: #3498db;");

        private final String iconLiteral;
        private final String style;

        ToastType(String iconLiteral, String style) {
            this.iconLiteral = iconLiteral;
            this.style = style;
        }

        public String getIconLiteral() {
            return iconLiteral;
        }

        public String getStyle() {
            return style;
        }
    }

    /**
     * Shows a toast notification.
     * 
     * @param message the message to display
     * @param type    the toast type (success, error, warning, info)
     */
    public static void show(String message, ToastType type) {
        show(message, type, 3000);
    }

    /**
     * Shows a toast notification with custom duration.
     * 
     * @param message    the message to display
     * @param type       the toast type
     * @param durationMs duration in milliseconds
     */
    public static void show(String message, ToastType type, int durationMs) {
        javafx.application.Platform.runLater(() -> {
            Stage toastStage = new Stage();
            toastStage.initStyle(StageStyle.TRANSPARENT);
            toastStage.setAlwaysOnTop(true);

            // Create toast content
            VBox toastBox = new VBox(8);
            toastBox.setAlignment(Pos.CENTER_LEFT);
            toastBox.setStyle(type.getStyle() + " -fx-background-radius: 8px; -fx-padding: 14px 20px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 4);");

            // Icon
            FontIcon icon = new FontIcon(type.getIconLiteral());
            icon.setIconSize(20);
            icon.setIconColor(javafx.scene.paint.Color.WHITE);

            // Message label
            Label messageLabel = new Label(message);
            messageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: 500;");
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(300);

            // Combine icon and message horizontally
            javafx.scene.layout.HBox content = new javafx.scene.layout.HBox(12);
            content.setAlignment(Pos.CENTER_LEFT);
            content.getChildren().addAll(icon, messageLabel);

            toastBox.getChildren().add(content);

            StackPane root = new StackPane(toastBox);
            root.setStyle("-fx-background-color: transparent;");

            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            toastStage.setScene(scene);

            // Position at bottom right
            toastStage.setX(javafx.stage.Screen.getPrimary().getVisualBounds().getMaxX() - 350);
            toastStage.setY(javafx.stage.Screen.getPrimary().getVisualBounds().getMaxY() - 120);

            // Fade in animation
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toastBox);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();

            // Auto dismiss after duration
            Timeline timeline = new Timeline(new javafx.animation.KeyFrame(
                    Duration.millis(durationMs),
                    event -> {
                        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toastBox);
                        fadeOut.setFromValue(1.0);
                        fadeOut.setToValue(0.0);
                        fadeOut.setOnFinished(e -> toastStage.close());
                        fadeOut.play();
                    }));
            timeline.play();

            toastStage.show();
        });
    }

    /**
     * Shows a success toast notification.
     * 
     * @param message the success message
     */
    public static void showSuccess(String message) {
        show(message, ToastType.SUCCESS);
    }

    /**
     * Shows an error toast notification.
     * 
     * @param message the error message
     */
    public static void showError(String message) {
        show(message, ToastType.ERROR);
    }

    /**
     * Shows a warning toast notification.
     * 
     * @param message the warning message
     */
    public static void showWarning(String message) {
        show(message, ToastType.WARNING);
    }

    /**
     * Shows an info toast notification.
     * 
     * @param message the info message
     */
    public static void showInfo(String message) {
        show(message, ToastType.INFO);
    }
}
