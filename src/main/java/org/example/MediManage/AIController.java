package org.example.MediManage;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.service.ai.LocalAIService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

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
    private CheckBox webSearchCheck;
    @FXML
    private Label modelStatusLabel;

    private final AIOrchestrator aiOrchestrator;
    private final LocalAIService localService;
    private boolean modelAutoLoadTriggered = false;
    private HBox typingIndicator;
    private Timeline typingAnimation;

    public AIController() {
        this.aiOrchestrator = AIServiceProvider.get().getOrchestrator();
        this.localService = AIServiceProvider.get().getLocalService();
    }

    @FXML
    public void initialize() {
        // Auto-scroll to bottom
        chatBox.heightProperty().addListener((obs, o, n) -> scrollPane.setVvalue(1.0));

        // Welcome message
        addSystemMessage("🤖 MediManage AI Assistant — Ask about inventory, stock, sales, or any medical question.");

        // Check & display model status
        updateModelStatus();

        // Auto-load model in background
        ensureModelLoaded();
    }

    // ======================== MODEL STATUS ========================

    private void updateModelStatus() {
        CompletableFuture.runAsync(() -> {
            try {
                if (localService != null && localService.isAvailable()) {
                    var client = java.net.http.HttpClient.newHttpClient();
                    var req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:5000/health"))
                            .GET().build();
                    var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        org.json.JSONObject h = new org.json.JSONObject(resp.body());
                        boolean loaded = h.optBoolean("model_loaded", false);
                        String provider = h.optString("provider", "none");
                        Platform.runLater(() -> {
                            if (modelStatusLabel != null) {
                                if (loaded) {
                                    modelStatusLabel.setText("✅ Model: " + provider);
                                    modelStatusLabel.setStyle("-fx-text-fill: #43e97b; -fx-font-size: 11px;");
                                } else {
                                    modelStatusLabel.setText("⏳ Model loading...");
                                    modelStatusLabel.setStyle("-fx-text-fill: #ffd700; -fx-font-size: 11px;");
                                }
                            }
                        });
                    }
                } else {
                    Platform.runLater(() -> {
                        if (modelStatusLabel != null) {
                            modelStatusLabel.setText("🔴 Engine offline");
                            modelStatusLabel.setStyle("-fx-text-fill: #f5576c; -fx-font-size: 11px;");
                        }
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (modelStatusLabel != null) {
                        modelStatusLabel.setText("🔴 Engine starting...");
                        modelStatusLabel.setStyle("-fx-text-fill: #f5576c; -fx-font-size: 11px;");
                    }
                });
            }
        });
    }

    private void ensureModelLoaded() {
        if (modelAutoLoadTriggered)
            return;
        modelAutoLoadTriggered = true;

        CompletableFuture.runAsync(() -> {
            try {
                if (localService != null && localService.isAvailable()) {
                    var client = java.net.http.HttpClient.newHttpClient();
                    var req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:5000/health"))
                            .GET().build();
                    var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        org.json.JSONObject h = new org.json.JSONObject(resp.body());
                        if (!h.optBoolean("model_loaded", false)) {
                            Platform.runLater(() -> addSystemMessage("⏳ Loading AI model in background..."));
                            localService.loadModel();

                            // Poll until model is loaded, then update status
                            for (int i = 0; i < 60; i++) {
                                Thread.sleep(2000);
                                var resp2 = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                                if (resp2.statusCode() == 200) {
                                    org.json.JSONObject h2 = new org.json.JSONObject(resp2.body());
                                    if (h2.optBoolean("model_loaded", false)) {
                                        String prov = h2.optString("provider", "loaded");
                                        Platform.runLater(() -> {
                                            addSystemMessage("✅ AI model ready (" + prov + ")");
                                            if (modelStatusLabel != null) {
                                                modelStatusLabel.setText("✅ Model: " + prov);
                                                modelStatusLabel
                                                        .setStyle("-fx-text-fill: #43e97b; -fx-font-size: 11px;");
                                            }
                                        });
                                        break;
                                    }
                                }
                            }
                        } else {
                            Platform.runLater(() -> updateModelStatus());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Auto-load check failed: " + e.getMessage());
            }
        });
    }

    // ======================== SEND MESSAGE ========================

    @FXML
    private void handleSendMessage() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty())
            return;
        sendMessage(msg);
    }

    private void sendMessage(String msg) {
        boolean useSearch = webSearchCheck.isSelected();

        addUserMessage(msg);
        inputField.clear();
        sendButton.setDisable(true);
        showTypingIndicator();

        boolean requiresPrecision = msg.toLowerCase().contains("dosage") ||
                msg.toLowerCase().contains("side effect") ||
                msg.toLowerCase().contains("interaction");

        aiOrchestrator.processQuery(msg, requiresPrecision, useSearch)
                .thenAccept(response -> Platform.runLater(() -> {
                    removeTypingIndicator();
                    addAIMessage(response);
                    sendButton.setDisable(false);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        removeTypingIndicator();
                        addSystemMessage("❌ Error: " + ex.getMessage());
                        sendButton.setDisable(false);
                    });
                    return null;
                });
    }

    /**
     * Two-phase quick report:
     * Phase 1 — Instant DB data (sub-second)
     * Phase 2 — AI-generated analysis and insights (background)
     */
    private void sendDbQuery(String queryPrompt, String displayName) {
        addUserMessage(displayName);
        inputField.clear();
        sendButton.setDisable(true);
        showTypingIndicator();

        localService.queryDatabase(queryPrompt)
                .thenAccept(dbData -> Platform.runLater(() -> {
                    removeTypingIndicator();
                    // Phase 1: Show raw DB data instantly
                    addReportMessage(displayName, dbData);

                    // Phase 2: Send to AI for polished analysis
                    showTypingIndicator();
                    String aiPrompt = buildAnalysisPrompt(displayName, dbData);
                    localService.chatWithContext(aiPrompt, dbData)
                            .thenAccept(aiResponse -> Platform.runLater(() -> {
                                removeTypingIndicator();
                                addAIAnalysisMessage(aiResponse);
                                sendButton.setDisable(false);
                            }))
                            .exceptionally(ex -> {
                                Platform.runLater(() -> {
                                    removeTypingIndicator();
                                    // AI not available is fine — DB data already shown
                                    addSystemMessage("💡 AI analysis unavailable — raw data shown above.");
                                    sendButton.setDisable(false);
                                });
                                return null;
                            });
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        removeTypingIndicator();
                        addSystemMessage("❌ DB query failed: " + ex.getMessage());
                        sendButton.setDisable(false);
                    });
                    return null;
                });
    }

    /**
     * Build a focused prompt for AI analysis based on the report type.
     */
    private String buildAnalysisPrompt(String reportType, String dbData) {
        if (reportType.contains("Inventory")) {
            return "Analyze this pharmacy inventory data. Summarize the key findings: " +
                    "total medicines shown, price range, stock levels. " +
                    "Flag any concerns and give 2-3 actionable recommendations.";
        } else if (reportType.contains("Low Stock")) {
            return "Analyze these low stock items. Which medicines need urgent reordering? " +
                    "Prioritize by criticality. Give specific reorder recommendations.";
        } else if (reportType.contains("Expiring")) {
            return "Analyze these expiring medicines. Which should be discounted for quick sale? " +
                    "Which should be returned to supplier? Prioritize by urgency.";
        } else if (reportType.contains("Sales")) {
            return "Analyze this sales data. How is today's performance? " +
                    "Compare with the 30-day trend. Any insights or suggestions?";
        } else if (reportType.contains("Customer")) {
            return "Analyze customer balances. Who are the highest debtors? " +
                    "Suggest a follow-up strategy for debt recovery.";
        }
        return "Analyze this pharmacy data and provide a helpful summary with actionable insights.";
    }

    // ======================== QUICK REPORT HANDLERS ========================

    @FXML
    private void handleInventoryReport() {
        sendDbQuery("Show inventory summary - list top medicines with stock quantities and prices",
                "📦 Inventory Summary");
    }

    @FXML
    private void handleLowStockReport() {
        sendDbQuery("Show low stock medicines that are running out",
                "⚠️ Low Stock Alert");
    }

    @FXML
    private void handleExpiryReport() {
        sendDbQuery("Show medicines expiring soon within the next 90 days",
                "⏰ Expiring Medicines");
    }

    @FXML
    private void handleSalesReport() {
        sendDbQuery("Show today's sales summary and revenue",
                "💰 Sales Report");
    }

    @FXML
    private void handleCustomerReport() {
        sendDbQuery("Show customer balances and outstanding debts",
                "👥 Customer Balances");
    }

    @FXML
    private void handleTopSellersReport() {
        sendDbQuery("Show top 20 best-selling medicines by total quantity sold from bill items",
                "🏆 Top Selling Medicines");
    }

    @FXML
    private void handleProfitReport() {
        sendDbQuery(
                "Show profit analysis - total revenue, total bills, average bill value, and revenue by payment mode",
                "📊 Profit Analysis");
    }

    @FXML
    private void handlePrescriptionReport() {
        sendDbQuery("Show recent prescriptions with patient name, doctor, status, and medicines prescribed",
                "📋 Prescription Overview");
    }

    @FXML
    private void handleDrugInteractions() {
        sendDbQuery(
                "Show recent bills with multiple medicines to check for potential drug-drug interactions. List patient and all medicines per bill",
                "💊 Drug Interaction Check");
    }

    @FXML
    private void handleReorderSuggestions() {
        sendDbQuery(
                "Show medicines with stock below 20 units that have been sold recently - suggest reorder quantities based on past sales velocity",
                "🔄 Reorder Suggestions");
    }

    @FXML
    private void handleDailySummary() {
        sendDbQuery(
                "Give a complete daily summary: total sales today, number of bills, new customers, pending prescriptions, low stock alerts, and expiring medicines count",
                "📅 Daily Summary");
    }

    @FXML
    private void handleClearChat() {
        chatBox.getChildren().clear();
        addSystemMessage("🤖 Chat cleared. Ask me anything about your pharmacy.");
    }

    // ======================== TYPING INDICATOR ========================

    private void showTypingIndicator() {
        typingIndicator = new HBox(8);
        typingIndicator.setAlignment(Pos.CENTER_LEFT);
        typingIndicator.setPadding(new Insets(5, 5, 5, 10));

        Label dots = new Label("🤖 Thinking");
        dots.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12px; -fx-font-style: italic;");

        typingAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> dots.setText("🤖 Thinking")),
                new KeyFrame(Duration.seconds(0.5), e -> dots.setText("🤖 Thinking.")),
                new KeyFrame(Duration.seconds(1.0), e -> dots.setText("🤖 Thinking..")),
                new KeyFrame(Duration.seconds(1.5), e -> dots.setText("🤖 Thinking...")));
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

    // ======================== MESSAGE BUILDERS ========================

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
        msgLabel.setStyle(
                "-fx-background-color: linear-gradient(to right, #667eea, #764ba2);" +
                        "-fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 16 16 4 16;" +
                        "-fx-font-size: 13px;");

        Label timeLabel = new Label(timestamp());
        timeLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 9px;");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);
        timeLabel.setMaxWidth(Double.MAX_VALUE);

        bubble.getChildren().addAll(msgLabel, timeLabel);
        box.getChildren().add(bubble);
        chatBox.getChildren().add(box);
    }

    private void addAIMessage(String text) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(4, 60, 4, 10));

        Label avatar = new Label("🤖");
        avatar.setStyle("-fx-font-size: 18px; -fx-padding: 4 0 0 0;");

        VBox bubble = new VBox(2);
        bubble.setMaxWidth(550);

        Label msgLabel = new Label(text);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(550);
        msgLabel.setStyle(
                "-fx-background-color: #2a2d3e;" +
                        "-fx-text-fill: #e0e0e0; -fx-padding: 10 14; -fx-background-radius: 16 16 16 4;" +
                        "-fx-font-size: 13px;");

        Label timeLabel = new Label(timestamp());
        timeLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 9px;");

        bubble.getChildren().addAll(msgLabel, timeLabel);
        box.getChildren().addAll(avatar, bubble);
        chatBox.getChildren().add(box);
    }

    /**
     * Render DB results as a proper styled table inside a report card.
     * Parses pipe-delimited rows into GridPane columns.
     */
    private void addReportMessage(String title, String text) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(4, 20, 4, 10));

        Label avatar = new Label("📊");
        avatar.setStyle("-fx-font-size: 18px; -fx-padding: 4 0 0 0;");

        VBox card = new VBox(8);
        card.setStyle(
                "-fx-background-color: #1e2130;" +
                        "-fx-padding: 14 16; -fx-background-radius: 12;" +
                        "-fx-border-color: #667eea; -fx-border-radius: 12; -fx-border-width: 1;");

        // Title
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #667eea; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Separator
        Separator sep = new Separator();

        // Parse data into table
        String[] lines = text.split("\n");
        String sectionHeader = "";
        java.util.List<String[]> dataRows = new java.util.ArrayList<>();
        String[] columnHeaders = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            // Section header like [Inventory Data - 20 medicines found]:
            if (line.startsWith("[") && line.contains("]")) {
                sectionHeader = line.replaceAll("[\\[\\]:]+", "").trim();
                continue;
            }

            // Data row: " - Name (Generic) | Company: X | Price: Y | Stock: Z | Expiry: W"
            if (line.startsWith("-") || line.startsWith("  -")) {
                line = line.replaceFirst("^\\s*-\\s*", ""); // strip leading "- "
                String[] parts = line.split("\\|");

                // Auto-detect headers from first data row
                if (columnHeaders == null) {
                    columnHeaders = detectHeaders(parts);
                }

                // Clean values (remove "Key: " prefixes)
                String[] values = new String[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    String val = parts[i].trim();
                    // Strip key prefix like "Company: ", "Price: ", "Stock: "
                    int colonIdx = val.indexOf(':');
                    if (colonIdx > 0 && colonIdx < 15) {
                        val = val.substring(colonIdx + 1).trim();
                    }
                    values[i] = val;
                }
                dataRows.add(values);
            }
        }

        // Section info
        if (!sectionHeader.isEmpty()) {
            Label secLabel = new Label(sectionHeader);
            secLabel.setStyle("-fx-text-fill: #ffd700; -fx-font-size: 12px; -fx-font-weight: bold;");
            card.getChildren().addAll(titleLabel, sep, secLabel);
        } else {
            card.getChildren().addAll(titleLabel, sep);
        }

        // Build table if we have data
        if (!dataRows.isEmpty() && columnHeaders != null) {
            GridPane table = new GridPane();
            table.setHgap(0);
            table.setVgap(0);

            int numCols = columnHeaders.length;

            // Header row
            for (int c = 0; c < numCols; c++) {
                Label h = new Label(columnHeaders[c]);
                h.setStyle(
                        "-fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-font-weight: bold;" +
                                "-fx-padding: 6 10; -fx-background-color: #2d3154;");
                h.setMaxWidth(Double.MAX_VALUE);
                h.setMinWidth(50);
                GridPane.setHgrow(h, Priority.ALWAYS);
                GridPane.setFillWidth(h, true);
                table.add(h, c, 0);
            }

            // Data rows
            for (int r = 0; r < dataRows.size(); r++) {
                String[] vals = dataRows.get(r);
                String bgColor = (r % 2 == 0) ? "#1a1d30" : "#22253a";

                for (int c = 0; c < numCols; c++) {
                    String val = (c < vals.length) ? vals[c] : "";
                    Label cell = new Label(val);
                    cell.setWrapText(true);
                    cell.setStyle(
                            "-fx-text-fill: #d0d0d0; -fx-font-size: 11px;" +
                                    "-fx-padding: 5 10; -fx-background-color: " + bgColor + ";");
                    cell.setMaxWidth(Double.MAX_VALUE);
                    cell.setMinWidth(50);
                    GridPane.setHgrow(cell, Priority.ALWAYS);
                    GridPane.setFillWidth(cell, true);
                    table.add(cell, c, r + 1);
                }
            }

            // Wrap in ScrollPane for wide tables
            ScrollPane tableScroll = new ScrollPane(table);
            tableScroll.setFitToWidth(true);
            tableScroll.setMaxHeight(300);
            tableScroll.setStyle("-fx-background: #1e2130; -fx-background-color: #1e2130;");

            // Row count
            Label countLabel = new Label(dataRows.size() + " rows");
            countLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");

            card.getChildren().addAll(tableScroll, countLabel);
        } else {
            // Fallback: show raw text
            Label raw = new Label(text);
            raw.setWrapText(true);
            raw.setMaxWidth(550);
            raw.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 12px;");
            card.getChildren().add(raw);
        }

        // Timestamp
        Label timeLabel = new Label("⚡ Instant DB Query • " + timestamp());
        timeLabel.setStyle("-fx-text-fill: #43e97b; -fx-font-size: 9px; -fx-padding: 4 0 0 0;");
        card.getChildren().add(timeLabel);

        box.getChildren().addAll(avatar, card);
        chatBox.getChildren().add(box);
    }

    /**
     * Auto-detect column headers from the first data row.
     * Rows look like: "Name (Generic) | Company: X | Price: Y | Stock: Z"
     */
    private String[] detectHeaders(String[] parts) {
        String[] headers = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            int colonIdx = p.indexOf(':');
            if (colonIdx > 0 && colonIdx < 15) {
                headers[i] = p.substring(0, colonIdx).trim();
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
        box.setPadding(new Insets(3));

        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        box.getChildren().add(label);
        chatBox.getChildren().add(box);
    }

    /**
     * AI Analysis card — styled distinctly from the raw DB report.
     * Shows the AI-generated insights with a sparkle accent.
     */
    private void addAIAnalysisMessage(String text) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(4, 30, 4, 10));

        Label avatar = new Label("✨");
        avatar.setStyle("-fx-font-size: 18px; -fx-padding: 4 0 0 0;");

        VBox card = new VBox(6);
        card.setMaxWidth(600);
        card.setStyle(
                "-fx-background-color: #1a1e2e;" +
                        "-fx-padding: 12 16; -fx-background-radius: 12;" +
                        "-fx-border-color: #a855f7; -fx-border-radius: 12; -fx-border-width: 1;");

        // Header
        Label header = new Label("✨ AI Analysis");
        header.setStyle("-fx-text-fill: #a855f7; -fx-font-size: 13px; -fx-font-weight: bold;");

        // Separator
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333;");

        // AI response text
        Label msgLabel = new Label(text);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(560);
        msgLabel.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 12.5px; -fx-line-spacing: 3;");

        // Timestamp
        Label timeLabel = new Label("🤖 AI-generated • " + timestamp());
        timeLabel.setStyle("-fx-text-fill: #a855f7; -fx-font-size: 9px; -fx-padding: 4 0 0 0;");

        card.getChildren().addAll(header, sep, msgLabel, timeLabel);
        box.getChildren().addAll(avatar, card);
        chatBox.getChildren().add(box);
    }
}
