package org.example.MediManage.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.example.MediManage.security.CloudApiKeyStore;
import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.service.ai.AssistantReportService;
import org.example.MediManage.util.AIHtmlRenderer;
import org.json.JSONObject;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

public class AIController {
    @FXML private VBox chatBox;
    @FXML private TextField inputField;
    @FXML private Button sendButton;
    @FXML private ScrollPane scrollPane;
    @FXML private CheckBox webSearchCheck;
    @FXML private Button stopButton;

    private final AIOrchestrator aiOrchestrator;
    private final AssistantReportService assistantReportService;
    private CompletableFuture<?> currentRequest;
    private HBox typingIndicator;
    private Timeline typingAnimation;

    public AIController() {
        this.aiOrchestrator = AIServiceProvider.get().getOrchestrator();
        this.assistantReportService = new AssistantReportService();
    }

    @FXML
    public void initialize() {
        chatBox.heightProperty().addListener((obs, oldValue, newValue) -> scrollPane.setVvalue(1.0));
        addSystemMessage("MediManage AI Assistant");
        addSystemMessage("Cloud AI runs through the Python backend. Configure provider and API key in Settings.");
    }

    @FXML
    private void handleSendMessage() {
        String msg = inputField.getText() == null ? "" : inputField.getText().trim();
        if (!msg.isEmpty()) {
            sendMessage(msg);
        }
    }

    private void sendMessage(String msg) {
        addUserMessage(msg);
        inputField.clear();
        sendButton.setDisable(true);
        setStopButtonVisible(true);
        showTypingIndicator();

        if (!aiOrchestrator.isEngineAvailable()) {
            removeTypingIndicator();
            addSystemMessage("Python AI backend is still starting. Try again in a moment.");
            sendButton.setDisable(false);
            setStopButtonVisible(false);
            return;
        }

        JSONObject data = new JSONObject().put("prompt", msg);
        currentRequest = aiOrchestrator.processOrchestration("raw_chat", data, "cloud_only", webSearchCheck.isSelected())
                .thenAccept(response -> Platform.runLater(() -> {
                    removeTypingIndicator();
                    addAIMessage(response, currentModeLabel());
                    sendButton.setDisable(false);
                    setStopButtonVisible(false);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        removeTypingIndicator();
                        sendButton.setDisable(false);
                        setStopButtonVisible(false);
                        if (!(ex instanceof CancellationException) && !(ex.getCause() instanceof CancellationException)) {
                            addSystemMessage("Error: " + ex.getMessage());
                        }
                    });
                    return null;
                });
    }

    @FXML
    private void handleStopGeneration() {
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
            aiOrchestrator.cancelGeneration();
            addSystemMessage("Generation stopped.");
            removeTypingIndicator();
            sendButton.setDisable(false);
            setStopButtonVisible(false);
        }
    }

    private void sendDbQuery(String displayName) {
        addUserMessage(displayName);
        inputField.clear();
        sendButton.setDisable(true);
        showTypingIndicator();

        assistantReportService.generate(resolveReportType(displayName))
                .thenAccept(dbData -> Platform.runLater(() -> {
                    removeTypingIndicator();
                    addReportMessage(displayName, dbData);
                    if (!aiOrchestrator.isEngineAvailable()) {
                        addSystemMessage("AI summary skipped because the Python backend is offline.");
                        sendButton.setDisable(false);
                        return;
                    }

                    setStopButtonVisible(true);
                    showTypingIndicator();

                    JSONObject data = new JSONObject()
                            .put("prompt", buildAnalysisPrompt(displayName))
                            .put("business_context", dbData);

                    currentRequest = aiOrchestrator.processOrchestration("raw_chat", data, "cloud_only", false)
                            .thenAccept(aiResponse -> Platform.runLater(() -> {
                                removeTypingIndicator();
                                addAIAnalysisMessage(aiResponse, currentModeLabel());
                                sendButton.setDisable(false);
                                setStopButtonVisible(false);
                            }))
                            .exceptionally(ex -> {
                                Platform.runLater(() -> {
                                    removeTypingIndicator();
                                    sendButton.setDisable(false);
                                    setStopButtonVisible(false);
                                    if (!(ex instanceof CancellationException) && !(ex.getCause() instanceof CancellationException)) {
                                        addSystemMessage("AI analysis unavailable. Raw data is shown above.");
                                    }
                                });
                                return null;
                            });
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        removeTypingIndicator();
                        addSystemMessage("Report generation failed: " + ex.getMessage());
                        sendButton.setDisable(false);
                        setStopButtonVisible(false);
                    });
                    return null;
                });
    }

    private String buildAnalysisPrompt(String displayName) {
        if (displayName.contains("Inventory")) {
            return "Analyze this inventory report. Summarize the key findings and give practical stock actions.";
        }
        if (displayName.contains("Low Stock")) {
            return "Analyze this low-stock report and prioritize urgent reorders.";
        }
        if (displayName.contains("Expir")) {
            return "Analyze this expiry report and suggest practical clearance or return actions.";
        }
        if (displayName.contains("Sales")) {
            return "Analyze this sales report and summarize performance trends and next actions.";
        }
        if (displayName.contains("Customer")) {
            return "Analyze these customer balances and suggest a simple follow-up strategy.";
        }
        if (displayName.contains("Top Selling")) {
            return "Analyze these top-selling medicines and explain what the demand trend suggests.";
        }
        if (displayName.contains("Profit")) {
            return "Analyze this profit report and identify pricing or inventory actions.";
        }
        if (displayName.contains("Interaction")) {
            return "Analyze this drug interaction report and highlight the most important safety points.";
        }
        if (displayName.contains("Reorder")) {
            return "Analyze these reorder suggestions and prioritize the most critical purchase actions.";
        }
        return "Analyze this pharmacy report and provide a concise summary with actionable insights.";
    }

    @FXML private void handleInventoryReport() { sendDbQuery("Inventory Summary"); }
    @FXML private void handleLowStockReport() { sendDbQuery("Low Stock Alert"); }
    @FXML private void handleExpiryReport() { sendDbQuery("Expiring Medicines"); }
    @FXML private void handleSalesReport() { sendDbQuery("Sales Report"); }
    @FXML private void handleCustomerReport() { sendDbQuery("Customer Balances"); }
    @FXML private void handleTopSellersReport() { sendDbQuery("Top Selling Medicines"); }
    @FXML private void handleProfitReport() { sendDbQuery("Profit Analysis"); }
    @FXML private void handleDrugInteractions() { sendDbQuery("Drug Interaction Check"); }
    @FXML private void handleReorderSuggestions() { sendDbQuery("Reorder Suggestions"); }
    @FXML private void handleDailySummary() { sendDbQuery("Daily Summary"); }

    @FXML
    private void handleClearChat() {
        chatBox.getChildren().clear();
        addSystemMessage("Chat cleared. Ask about inventory, sales, customers, or pharmacy operations.");
    }

    private void setStopButtonVisible(boolean visible) {
        if (stopButton != null) {
            stopButton.setVisible(visible);
            stopButton.setManaged(visible);
        }
    }

    private void showTypingIndicator() {
        typingIndicator = new HBox(8);
        typingIndicator.setAlignment(Pos.CENTER_LEFT);
        typingIndicator.setPadding(new Insets(5, 5, 5, 10));

        Label dots = new Label("AI thinking");
        dots.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12px; -fx-font-style: italic;");

        typingAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> dots.setText("AI thinking")),
                new KeyFrame(Duration.seconds(0.5), e -> dots.setText("AI thinking.")),
                new KeyFrame(Duration.seconds(1.0), e -> dots.setText("AI thinking..")),
                new KeyFrame(Duration.seconds(1.5), e -> dots.setText("AI thinking...")));
        typingAnimation.setCycleCount(Animation.INDEFINITE);
        typingAnimation.play();

        typingIndicator.getChildren().add(dots);
        chatBox.getChildren().add(typingIndicator);
    }

    private void removeTypingIndicator() {
        if (typingAnimation != null) {
            typingAnimation.stop();
            typingAnimation = null;
        }
        if (typingIndicator != null) {
            chatBox.getChildren().remove(typingIndicator);
            typingIndicator = null;
        }
    }

    private String timestamp() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void addUserMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(4, 10, 4, 60));

        VBox bubble = new VBox(2);
        bubble.setMaxWidth(500);

        Label msgLabel = new Label(text);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(500);
        msgLabel.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);"
                + "-fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 16 16 4 16;"
                + "-fx-font-size: 13px;");

        Label timeLabel = new Label(timestamp());
        timeLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 9px;");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);
        timeLabel.setMaxWidth(Double.MAX_VALUE);

        bubble.getChildren().addAll(msgLabel, timeLabel);
        box.getChildren().add(bubble);
        chatBox.getChildren().add(box);
    }

    private void addReportMessage(String title, String text) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(4, 20, 4, 10));

        Label avatar = createAvatarLabel("DB", "#667eea", "rgba(102,126,234,0.16)", "rgba(102,126,234,0.30)");

        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: #1e2130;"
                + "-fx-padding: 14 16; -fx-background-radius: 12;"
                + "-fx-border-color: #667eea; -fx-border-radius: 12; -fx-border-width: 1;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #667eea; -fx-font-size: 14px; -fx-font-weight: bold;");
        Separator sep = new Separator();

        String[] lines = text.split("\n");
        String sectionHeader = "";
        java.util.List<String[]> dataRows = new java.util.ArrayList<>();
        String[] columnHeaders = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.contains("]")) {
                sectionHeader = trimmed.replaceAll("[\\[\\]:]+", "").trim();
                continue;
            }
            if (trimmed.startsWith("-") || trimmed.startsWith("  -")) {
                String cleaned = trimmed.replaceFirst("^\\s*-\\s*", "");
                String[] parts = cleaned.split("\\|");
                if (columnHeaders == null) {
                    columnHeaders = detectHeaders(parts);
                }
                String[] values = new String[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    String value = parts[i].trim();
                    int colonIdx = value.indexOf(':');
                    if (colonIdx > 0 && colonIdx < 15) {
                        value = value.substring(colonIdx + 1).trim();
                    }
                    values[i] = value;
                }
                dataRows.add(values);
            }
        }

        if (!sectionHeader.isEmpty()) {
            Label secLabel = new Label(sectionHeader);
            secLabel.setStyle("-fx-text-fill: #ffd700; -fx-font-size: 12px; -fx-font-weight: bold;");
            card.getChildren().addAll(titleLabel, sep, secLabel);
        } else {
            card.getChildren().addAll(titleLabel, sep);
        }

        if (!dataRows.isEmpty() && columnHeaders != null) {
            GridPane table = new GridPane();
            table.setHgap(0);
            table.setVgap(0);

            for (int c = 0; c < columnHeaders.length; c++) {
                Label header = new Label(columnHeaders[c]);
                header.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-font-weight: bold;"
                        + "-fx-padding: 6 10; -fx-background-color: #2d3154;");
                header.setMaxWidth(Double.MAX_VALUE);
                header.setMinWidth(50);
                GridPane.setHgrow(header, Priority.ALWAYS);
                GridPane.setFillWidth(header, true);
                table.add(header, c, 0);
            }

            for (int r = 0; r < dataRows.size(); r++) {
                String[] row = dataRows.get(r);
                String bgColor = (r % 2 == 0) ? "#1a1d30" : "#22253a";
                for (int c = 0; c < columnHeaders.length; c++) {
                    String value = c < row.length ? row[c] : "";
                    Label cell = new Label(value);
                    cell.setWrapText(true);
                    cell.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;"
                            + "-fx-padding: 5 10; -fx-background-color: " + bgColor + ";");
                    cell.setMaxWidth(Double.MAX_VALUE);
                    cell.setMinWidth(50);
                    GridPane.setHgrow(cell, Priority.ALWAYS);
                    GridPane.setFillWidth(cell, true);
                    table.add(cell, c, r + 1);
                }
            }

            ScrollPane tableScroll = new ScrollPane(table);
            tableScroll.setFitToWidth(true);
            tableScroll.setMaxHeight(300);
            tableScroll.setStyle("-fx-background: #1e2130; -fx-background-color: #1e2130;");

            Label countLabel = new Label(dataRows.size() + " rows");
            countLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
            card.getChildren().addAll(tableScroll, countLabel);
        } else {
            Label raw = new Label(text);
            raw.setWrapText(true);
            raw.setMaxWidth(550);
            raw.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 12px;");
            card.getChildren().add(raw);
        }

        Label timeLabel = new Label("DB Query | " + timestamp());
        timeLabel.setStyle("-fx-text-fill: #43e97b; -fx-font-size: 9px; -fx-padding: 4 0 0 0;");
        card.getChildren().add(timeLabel);

        box.getChildren().addAll(avatar, card);
        chatBox.getChildren().add(box);
    }

    private String[] detectHeaders(String[] parts) {
        String[] headers = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String value = parts[i].trim();
            int colonIdx = value.indexOf(':');
            if (colonIdx > 0 && colonIdx < 15) {
                headers[i] = value.substring(0, colonIdx).trim();
            } else if (i == 0) {
                headers[i] = "Name";
            } else {
                headers[i] = "Col " + (i + 1);
            }
        }
        return headers;
    }

    private void addSystemMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 6, 0));

        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #a7b0c8; -fx-font-size: 11px; -fx-background-color: rgba(255,255,255,0.04);"
                + "-fx-padding: 6 12; -fx-background-radius: 999; -fx-border-color: rgba(125,211,252,0.16);"
                + "-fx-border-radius: 999;");

        box.getChildren().add(label);
        chatBox.getChildren().add(box);
    }

    private void addAIAnalysisMessage(String text, String sourceLabel) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(4, 30, 4, 10));

        Label avatar = createAvatarLabel("AI", "#a855f7", "rgba(168,85,247,0.14)", "rgba(168,85,247,0.30)");

        VBox card = new VBox(6);
        card.setMaxWidth(600);
        card.setStyle("-fx-background-color: #1a1e2e;"
                + "-fx-padding: 12 16; -fx-background-radius: 12;"
                + "-fx-border-color: #a855f7; -fx-border-radius: 12; -fx-border-width: 1;");

        Label header = new Label("AI Analysis");
        header.setStyle("-fx-text-fill: #a855f7; -fx-font-size: 13px; -fx-font-weight: bold;");
        Separator sep = new Separator();

        WebView webView = new WebView();
        webView.setMaxWidth(560);
        webView.setPrefHeight(30);
        webView.getEngine().loadContent(AIHtmlRenderer.toHtmlDocument(text, AIHtmlRenderer.Theme.PANEL));
        webView.getEngine().documentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (newDoc != null) {
                Platform.runLater(() -> resizeWebView(webView));
            }
        });

        Label timeLabel = new Label(sourceLabel + " | " + timestamp());
        timeLabel.setStyle("-fx-text-fill: #a855f7; -fx-font-size: 9px; -fx-padding: 4 0 0 0;");

        card.getChildren().addAll(header, sep, webView, timeLabel);
        box.getChildren().addAll(avatar, card);
        chatBox.getChildren().add(box);
    }

    private void addAIMessage(String text, String sourceLabel) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(4, 30, 4, 10));

        Label avatar = createAvatarLabel("AI", "#7dd3fc", "rgba(125,211,252,0.12)", "rgba(125,211,252,0.25)");

        VBox bubble = new VBox(6);
        bubble.setMaxWidth(550);
        bubble.setStyle("-fx-background-color: #20263c;"
                + "-fx-padding: 10 12; -fx-background-radius: 18 18 18 4;"
                + "-fx-border-color: rgba(125,211,252,0.16); -fx-border-radius: 18 18 18 4;");

        Label sourceBadge = new Label(sourceLabel);
        sourceBadge.setStyle("-fx-text-fill: #7dd3fc; -fx-font-size: 10px; -fx-font-weight: bold;"
                + "-fx-background-color: rgba(125,211,252,0.10); -fx-background-radius: 999;"
                + "-fx-padding: 3 8;");

        WebView webView = new WebView();
        webView.setMaxWidth(530);
        webView.setPrefHeight(30);
        webView.getEngine().loadContent(AIHtmlRenderer.toHtmlDocument(text, AIHtmlRenderer.Theme.CHAT));
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> resizeWebView(webView));
            }
        });

        Label timeLabel = new Label(timestamp());
        timeLabel.setStyle("-fx-text-fill: #7580a4; -fx-font-size: 9px;");

        bubble.getChildren().addAll(sourceBadge, webView, timeLabel);
        box.getChildren().addAll(avatar, bubble);
        chatBox.getChildren().add(box);
    }

    private Label createAvatarLabel(String text, String textColor, String backgroundColor, String borderColor) {
        Label avatar = new Label(text);
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(32, 32);
        avatar.setPrefSize(32, 32);
        avatar.setMaxSize(32, 32);
        avatar.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 11px; -fx-font-weight: bold;"
                + "-fx-background-color: " + backgroundColor + "; -fx-border-color: " + borderColor + ";"
                + "-fx-background-radius: 999; -fx-border-radius: 999;");
        return avatar;
    }

    private void resizeWebView(WebView webView) {
        try {
            Object height = webView.getEngine().executeScript(
                    "document.documentElement.scrollHeight || document.body.scrollHeight");
            if (height instanceof Number number) {
                webView.setPrefHeight(number.doubleValue() + 20);
            }
        } catch (Exception ignored) {
        }
    }

    private AssistantReportService.ReportType resolveReportType(String displayName) {
        if (displayName.contains("Inventory")) return AssistantReportService.ReportType.INVENTORY;
        if (displayName.contains("Low Stock")) return AssistantReportService.ReportType.LOW_STOCK;
        if (displayName.contains("Expir")) return AssistantReportService.ReportType.EXPIRING;
        if (displayName.contains("Sales")) return AssistantReportService.ReportType.SALES;
        if (displayName.contains("Customer")) return AssistantReportService.ReportType.CUSTOMERS;
        if (displayName.contains("Top Selling")) return AssistantReportService.ReportType.TOP_SELLERS;
        if (displayName.contains("Profit")) return AssistantReportService.ReportType.PROFIT;
        if (displayName.contains("Interaction")) return AssistantReportService.ReportType.DRUG_INTERACTIONS;
        if (displayName.contains("Reorder")) return AssistantReportService.ReportType.REORDER;
        return AssistantReportService.ReportType.DAILY_SUMMARY;
    }

    private String currentModeLabel() {
        Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
        CloudApiKeyStore.Provider provider = AIOrchestrator.resolveCloudProvider(prefs.get("cloud_provider", "GEMINI"));
        String model = AIOrchestrator.resolveConfiguredCloudModel(provider, prefs.get("cloud_model", ""));
        return "☁ " + provider.name() + (model.isBlank() ? "" : " • " + model);
    }
}
