package org.example.MediManage;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.MediManage.service.ai.AIOrchestrator;

public class AIController {

    @FXML
    private VBox chatBox;
    @FXML
    private TextField inputField;
    @FXML
    private Button sendButton;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private javafx.scene.control.CheckBox webSearchCheck;

    private final AIOrchestrator aiOrchestrator;

    public AIController() {
        this.aiOrchestrator = new AIOrchestrator();
    }

    @FXML
    public void initialize() {
        // Auto-scroll to bottom
        chatBox.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));

        addSystemMessage(
                "Hello! I am your Duo AI Assistant. I can help with stock management (Local) or medical questions (Cloud).");
    }

    @FXML
    private void handleSendMessage() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty())
            return;

        boolean useSearch = webSearchCheck.isSelected();

        // User Message
        addUserMessage(msg);
        inputField.clear();
        sendButton.setDisable(true);

        // Determine if cloud is needed (simple heuristic for now)
        boolean requiresPrecision = msg.toLowerCase().contains("dosage") ||
                msg.toLowerCase().contains("side effect") ||
                msg.toLowerCase().contains("interaction");

        aiOrchestrator.processQuery(msg, requiresPrecision, useSearch)
                .thenAccept(response -> Platform.runLater(() -> {
                    addAIMessage(response);
                    sendButton.setDisable(false);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        addSystemMessage("Error: " + ex.getMessage());
                        sendButton.setDisable(false);
                    });
                    return null;
                });
    }

    private void addUserMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(5));

        Label label = new Label(text);
        label.setStyle(
                "-fx-background-color: #007bff; -fx-text-fill: white; -fx-padding: 8px; -fx-background-radius: 10px;");
        label.setWrapText(true);
        label.setMaxWidth(400);

        box.getChildren().add(label);
        chatBox.getChildren().add(box);
    }

    private void addAIMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(5));

        Label label = new Label(text);
        label.setStyle(
                "-fx-background-color: #e9ecef; -fx-text-fill: black; -fx-padding: 8px; -fx-background-radius: 10px;");
        label.setWrapText(true);
        label.setMaxWidth(400);

        box.getChildren().add(label);
        chatBox.getChildren().add(box);
    }

    private void addSystemMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(5));

        Label label = new Label(text);
        label.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");

        box.getChildren().add(label);
        chatBox.getChildren().add(box);
    }
}
