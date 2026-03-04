package org.example.MediManage.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Universal toast / snackbar notification system for MediManage.
 * <p>
 * Usage from any controller or background thread:
 * 
 * <pre>
 * ToastNotification.success("Settings Saved");
 * ToastNotification.info("Downloading model…");
 * ToastNotification.warning("Low Disk Space");
 * ToastNotification.error("AI Engine Failed");
 * </pre>
 * <p>
 * Call {@link #install(StackPane)} once from MainShellController to set the
 * overlay pane where toasts are rendered.
 */
public final class ToastNotification {

    // ── Toast Types ──────────────────────────────────────────────────────────
    public enum Type {
        SUCCESS("✓", "toast-success"),
        INFO("ℹ", "toast-info"),
        WARNING("⚠", "toast-warning"),
        ERROR("✕", "toast-error");

        final String icon;
        final String styleClass;

        Type(String icon, String styleClass) {
            this.icon = icon;
            this.styleClass = styleClass;
        }
    }

    // ── Configuration ────────────────────────────────────────────────────────
    private static final int MAX_VISIBLE = 5;
    private static final double DISPLAY_MS = 3500;
    private static final double FADE_IN_MS = 300;
    private static final double FADE_OUT_MS = 400;
    private static final double SLIDE_PX = 40;

    // ── State ────────────────────────────────────────────────────────────────
    private static StackPane overlayPane;
    private static VBox toastContainer;
    private static int visibleCount = 0;
    private static final Queue<Runnable> pendingQueue = new LinkedList<>();

    private ToastNotification() {
    }

    /**
     * Install the toast system into the given overlay StackPane.
     * Must be called once from the main shell controller's initialize().
     */
    public static void install(StackPane overlay) {
        overlayPane = overlay;

        toastContainer = new VBox(8);
        toastContainer.setAlignment(Pos.BOTTOM_RIGHT);
        toastContainer.setPadding(new Insets(0, 24, 24, 0));
        toastContainer.setPickOnBounds(false);
        toastContainer.setMouseTransparent(false);
        toastContainer.setMaxWidth(380);
        toastContainer.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane.setAlignment(toastContainer, Pos.BOTTOM_RIGHT);
        overlayPane.getChildren().add(toastContainer);
        overlayPane.setPickOnBounds(false);
    }

    // ── Convenience methods ──────────────────────────────────────────────────

    public static void success(String message) {
        show(Type.SUCCESS, message);
    }

    public static void info(String message) {
        show(Type.INFO, message);
    }

    public static void warning(String message) {
        show(Type.WARNING, message);
    }

    public static void error(String message) {
        show(Type.ERROR, message);
    }

    // ── Core show logic ──────────────────────────────────────────────────────

    public static void show(Type type, String message) {
        Runnable task = () -> showOnFxThread(type, message);
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    private static void showOnFxThread(Type type, String message) {
        if (overlayPane == null || toastContainer == null) {
            // Fallback: print to console if toast system not installed yet
            System.out.println("[Toast/" + type.name() + "] " + message);
            return;
        }

        if (visibleCount >= MAX_VISIBLE) {
            pendingQueue.add(() -> showOnFxThread(type, message));
            return;
        }

        HBox toast = createToastNode(type, message);
        toast.setOpacity(0);
        toast.setTranslateY(SLIDE_PX);

        toastContainer.getChildren().add(toast);
        visibleCount++;

        // ── Slide-in + Fade-in ──
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(FADE_IN_MS), toast);
        slideIn.setFromY(SLIDE_PX);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_MS), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ParallelTransition enterAnim = new ParallelTransition(slideIn, fadeIn);
        enterAnim.play();

        // ── Auto-dismiss after delay ──
        PauseTransition holdPause = new PauseTransition(Duration.millis(DISPLAY_MS));
        holdPause.setOnFinished(e -> dismissToast(toast));
        holdPause.play();

        // ── Click to dismiss early ──
        toast.setOnMouseClicked(e -> {
            holdPause.stop();
            dismissToast(toast);
        });
    }

    private static void dismissToast(HBox toast) {
        if (!toastContainer.getChildren().contains(toast)) {
            return; // Already dismissed
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_MS), toast);
        fadeOut.setFromValue(toast.getOpacity());
        fadeOut.setToValue(0);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(FADE_OUT_MS), toast);
        slideOut.setByY(SLIDE_PX / 2);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition exitAnim = new ParallelTransition(fadeOut, slideOut);
        exitAnim.setOnFinished(e -> {
            toastContainer.getChildren().remove(toast);
            visibleCount--;
            drainPendingQueue();
        });
        exitAnim.play();
    }

    private static void drainPendingQueue() {
        if (!pendingQueue.isEmpty() && visibleCount < MAX_VISIBLE) {
            Runnable next = pendingQueue.poll();
            if (next != null) {
                next.run();
            }
        }
    }

    // ── Toast node factory ───────────────────────────────────────────────────

    private static HBox createToastNode(Type type, String message) {
        // Icon
        Label iconLabel = new Label(type.icon);
        iconLabel.getStyleClass().addAll("toast-icon", type.styleClass);
        iconLabel.setMinWidth(28);
        iconLabel.setAlignment(Pos.CENTER);

        // Message
        Label msgLabel = new Label(message);
        msgLabel.getStyleClass().add("toast-message");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(300);

        // Container
        HBox toast = new HBox(10, iconLabel, msgLabel);
        toast.getStyleClass().addAll("toast-card", type.styleClass);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setPadding(new Insets(12, 18, 12, 14));
        toast.setCursor(javafx.scene.Cursor.HAND);

        return toast;
    }
}
