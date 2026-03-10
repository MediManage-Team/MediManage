package org.example.MediManage.util;

import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;

import java.util.concurrent.CompletionException;

/**
 * Shared async UI state transitions for AI actions.
 */
public final class AsyncUiFeedback {
    private static final String RETRY_HINT = "Please retry using the same action button.";

    private AsyncUiFeedback() {
    }

    public static void showLoading(Button actionButton, ProgressIndicator spinner, TextArea output,
            String busyButtonText, String loadingMessage) {
        if (output != null) {
            output.setText(loadingMessage);
        }
        if (actionButton != null) {
            actionButton.setDisable(true);
            if (busyButtonText != null && !busyButtonText.isBlank()) {
                actionButton.setText(busyButtonText);
            }
        }
        setSpinnerVisible(spinner, true);
    }

    public static void showSuccess(Button actionButton, ProgressIndicator spinner, TextArea output,
            String readyButtonText, String content) {
        if (output != null) {
            output.setText(content);
        }
        if (actionButton != null) {
            actionButton.setDisable(false);
            if (readyButtonText != null && !readyButtonText.isBlank()) {
                actionButton.setText(readyButtonText);
            }
        }
        setSpinnerVisible(spinner, false);
    }

    public static void showError(Button actionButton, ProgressIndicator spinner, TextArea output,
            String readyButtonText, Throwable error) {
        if (output != null) {
            output.setText("❌ Request failed.\n" + rootCauseMessage(error) + "\n\n" + RETRY_HINT);
        }
        if (actionButton != null) {
            actionButton.setDisable(false);
            if (readyButtonText != null && !readyButtonText.isBlank()) {
                actionButton.setText(readyButtonText);
            }
        }
        setSpinnerVisible(spinner, false);
    }

    private static void setSpinnerVisible(ProgressIndicator spinner, boolean visible) {
        if (spinner == null) {
            return;
        }
        spinner.setVisible(visible);
        spinner.setManaged(visible);
    }

    private static String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.toString() : message;
    }
}
