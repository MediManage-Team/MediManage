package org.example.MediManage.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.web.WebView;

import java.util.function.Consumer;

/**
 * Utility for showing AI tool results in styled popup dialogs.
 */
public class AIResultDialog {

    /**
     * Shows a popup dialog with a loading state, then updates with the result.
     */
    public static Stage showResultPopup(String title, String icon, String content) {
        Stage dialog = createBaseDialog(title, icon);

        WebView resultView = createResultView();
        loadResult(resultView, content);

        VBox root = createDialogLayout(title, icon, resultView, null);
        showDialog(dialog, root);
        return dialog;
    }

    /**
     * Shows a popup with a text input field and a search button.
     * The onSearch callback receives the input text.
     */
    public static Stage showSearchPopup(String title, String icon, String promptText,
                                         Consumer<SearchContext> onSearch) {
        Stage dialog = createBaseDialog(title, icon);

        TextField searchField = new TextField();
        searchField.setPromptText(promptText);
        searchField.setStyle("-fx-background-color: #1a1f3d; -fx-text-fill: #e0e6f0; " +
                "-fx-prompt-text-fill: #5a6380; -fx-border-color: #2d3555; -fx-border-radius: 6; " +
                "-fx-background-radius: 6; -fx-padding: 8 12; -fx-font-size: 13px;");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Search");
        searchBtn.setStyle("-fx-background-color: #00d4aa; -fx-text-fill: #0a0e1a; " +
                "-fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 20; " +
                "-fx-cursor: hand; -fx-font-size: 13px;");

        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);

        HBox searchBox = new HBox(10, searchField, searchBtn, loadingIndicator);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        WebView resultView = createResultView();
        loadResult(resultView, "Search medicine name, generic, or composition details here.");

        SearchContext ctx = new SearchContext(resultView, searchField, searchBtn, loadingIndicator);

        searchBtn.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) {
                ctx.setLoading(true, "Searching for \"" + query + "\"...");
                try {
                    onSearch.accept(ctx);
                } catch (RuntimeException ex) {
                    ctx.finish("Error: " + ex.getMessage());
                }
            }
        });
        searchField.setOnAction(e -> searchBtn.fire());

        VBox inputSection = new VBox(10, searchBox);
        VBox root = createDialogLayout(title, icon, resultView, inputSection);
        showDialog(dialog, root);
        searchField.requestFocus();
        return dialog;
    }

    /**
     * Shows a loading popup and returns context to update it later.
     */
    public static LoadingContext showLoadingPopup(String title, String icon, String loadingMessage) {
        Stage dialog = createBaseDialog(title, icon);

        WebView resultView = createResultView();
        loadResult(resultView, loadingMessage);

        VBox root = createDialogLayout(title, icon, resultView, null);
        showDialog(dialog, root);
        return new LoadingContext(dialog, resultView);
    }

    // ── Internal helpers ──

    private static Stage createBaseDialog(String title, String icon) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle(title);
        return dialog;
    }

    private static WebView createResultView() {
        WebView view = new WebView();
        view.setPrefHeight(420);
        view.setContextMenuEnabled(false);
        view.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private static void loadResult(WebView resultView, String content) {
        resultView.getEngine().loadContent(AIHtmlRenderer.toHtmlDocument(content, AIHtmlRenderer.Theme.DIALOG));
    }

    private static VBox createDialogLayout(String title, String icon, WebView resultView, VBox inputSection) {
        // Title bar
        String titleText = icon == null || icon.isBlank() ? title : icon.trim() + "  " + title;
        Label titleLabel = new Label(titleText);
        titleLabel.setStyle("-fx-text-fill: #e0e6f0; -fx-font-size: 16px; -fx-font-weight: bold;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #7a8399; " +
                "-fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 2 8;");

        HBox titleBar = new HBox(titleLabel, closeBtn);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(12, 16, 8, 16));
        titleBar.setStyle("-fx-background-color: #0f1628; -fx-border-color: #1e2536; " +
                "-fx-border-width: 0 0 1 0;");

        // Make title bar draggable
        final double[] dragDelta = new double[2];
        titleBar.setOnMousePressed(e -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            dragDelta[0] = stage.getX() - e.getScreenX();
            dragDelta[1] = stage.getY() - e.getScreenY();
        });
        titleBar.setOnMouseDragged(e -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(e.getScreenX() + dragDelta[0]);
            stage.setY(e.getScreenY() + dragDelta[1]);
        });

        VBox content = new VBox(10);
        content.setPadding(new Insets(12, 16, 16, 16));
        if (inputSection != null) {
            content.getChildren().add(inputSection);
        }
        content.getChildren().add(resultView);
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox root = new VBox(titleBar, content);
        root.setStyle("-fx-background-color: #131a2e; -fx-border-color: #2d3555; " +
                "-fx-border-radius: 10; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 20, 0, 0, 5);");
        root.setPrefWidth(620);
        root.setPrefHeight(480);

        closeBtn.setOnAction(e -> ((Stage) root.getScene().getWindow()).close());

        return root;
    }

    private static void showDialog(Stage dialog, VBox root) {
        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.show();
    }

    /**
     * Context for updating a loading popup with results.
     */
    public static class LoadingContext {
        private final Stage dialog;
        private final WebView resultView;

        LoadingContext(Stage dialog, WebView resultView) {
            this.dialog = dialog;
            this.resultView = resultView;
        }

        public void setResult(String text) {
            javafx.application.Platform.runLater(() -> loadResult(resultView, text));
        }

        public Stage getDialog() {
            return dialog;
        }
    }

    /**
     * Context for search popups — provides access to the result area.
     */
    public static class SearchContext {
        private final WebView resultView;
        private final TextField searchField;
        private final Button searchButton;
        private final ProgressIndicator loadingIndicator;

        SearchContext(WebView resultView, TextField searchField, Button searchButton, ProgressIndicator loadingIndicator) {
            this.resultView = resultView;
            this.searchField = searchField;
            this.searchButton = searchButton;
            this.loadingIndicator = loadingIndicator;
        }

        public void setResult(String text) {
            javafx.application.Platform.runLater(() -> loadResult(resultView, text));
        }

        public void setLoading(boolean loading, String message) {
            javafx.application.Platform.runLater(() -> {
                loadResult(resultView, message);
                searchButton.setDisable(loading);
                searchButton.setText(loading ? "Searching..." : "Search");
                loadingIndicator.setVisible(loading);
                loadingIndicator.setManaged(loading);
            });
        }

        public void finish(String text) {
            javafx.application.Platform.runLater(() -> {
                loadResult(resultView, text);
                searchButton.setDisable(false);
                searchButton.setText("Search");
                loadingIndicator.setVisible(false);
                loadingIndicator.setManaged(false);
            });
        }

        public String getQuery() {
            return searchField.getText().trim();
        }

        public WebView getResultView() {
            return resultView;
        }
    }
}
