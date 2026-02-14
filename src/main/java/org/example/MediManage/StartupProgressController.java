package org.example.MediManage;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

/**
 * Controller for the startup progress popup.
 * Only shown when environment setup is needed (first-time or missing deps).
 * Shows real-time logs and auto-closes when setup finishes.
 */
public class StartupProgressController {

    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private TextArea logArea;

    private Stage stage;
    private volatile boolean closed = false;

    /**
     * Create and show the startup progress popup.
     * MUST be called from the FX Application Thread.
     */
    public static StartupProgressController show() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    StartupProgressController.class.getResource("/org/example/MediManage/startup-progress-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 370);

            StartupProgressController controller = loader.getController();
            Stage popupStage = new Stage();
            popupStage.setTitle("MediManage — Setting Up AI");
            popupStage.setScene(scene);
            popupStage.setResizable(false);
            popupStage.setAlwaysOnTop(false);
            // Let user close it manually if they want
            popupStage.setOnCloseRequest(e -> controller.closed = true);
            controller.stage = popupStage;

            popupStage.show();
            return controller;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Append a log line (thread-safe). */
    public void appendLog(String message) {
        if (closed)
            return;
        Platform.runLater(() -> {
            if (logArea != null && !closed) {
                logArea.appendText(message + "\n");
            }
        });
    }

    /** Update the status label (thread-safe). */
    public void setStatus(String status) {
        if (closed)
            return;
        Platform.runLater(() -> {
            if (statusLabel != null && !closed) {
                statusLabel.setText(status);
            }
        });
    }

    /** Set progress bar value (-1 for indeterminate, 0.0–1.0 for determinate). */
    public void setProgress(double value) {
        if (closed)
            return;
        Platform.runLater(() -> {
            if (progressBar != null && !closed) {
                progressBar.setProgress(value);
            }
        });
    }

    /** Close the popup (thread-safe). */
    public void close() {
        if (closed)
            return;
        closed = true;
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });
    }

    public boolean isClosed() {
        return closed;
    }
}
