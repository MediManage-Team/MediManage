package org.example.MediManage.controller;

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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import javafx.scene.web.WebView;
import java.util.concurrent.CancellationException;
import java.io.File;

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
    private ComboBox<String> modelSelector;
    @FXML
    private ToggleGroup engineToggleGroup;
    @FXML
    private RadioButton cloudRadio;
    @FXML
    private RadioButton localRadio;
    @FXML
    private Button stopButton;

    private final AIOrchestrator aiOrchestrator;
    private CompletableFuture<?> currentRequest;

    private static final Parser MARKDOWN_PARSER;
    private static final HtmlRenderer HTML_RENDERER;
    static {
        MutableDataSet options = new MutableDataSet();
        MARKDOWN_PARSER = Parser.builder(options).build();
        HTML_RENDERER = HtmlRenderer.builder(options).build();
    }
    private boolean modelAutoLoadTriggered = false;
    private HBox typingIndicator;
    private Timeline typingAnimation;

    public AIController() {
        this.aiOrchestrator = AIServiceProvider.get().getOrchestrator();
    }

    @FXML
    public void initialize() {
        // Auto-scroll to bottom
        chatBox.heightProperty().addListener((obs, o, n) -> scrollPane.setVvalue(1.0));

        // Welcome message
        addSystemMessage("🤖 MediManage AI Assistant — Ask about inventory, stock, sales, or any medical question.");

        // Engine Toggle Listener
        if (engineToggleGroup != null) {
            engineToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                boolean useCloud = (newVal == cloudRadio);
                aiOrchestrator.setForceCloud(useCloud);
                addSystemMessage("⚡ Routing set to " + (useCloud ? "Cloud Only" : "Auto/Local") + ".");
                if (!useCloud) {
                    ensureModelLoaded();
                }
            });
            // Apply UI initial selected value
            aiOrchestrator.setForceCloud(engineToggleGroup.getSelectedToggle() == cloudRadio);
        }

        // Check & display model status
        updateModelStatus();

        // Auto-load model in background
        ensureModelLoaded();
    }

    // ======================== MODEL STATUS ========================

    private void updateModelStatus() {
        CompletableFuture.runAsync(() -> {
            try {
                if (aiOrchestrator.isLocalAvailable()) {
                    org.json.JSONObject health = aiOrchestrator.getLocalHealth();
                    boolean loaded = health.optBoolean("model_loaded", false);
                    String provider = health.optString("provider", "none");
                    
                    org.json.JSONArray models = aiOrchestrator.listLocalModels();
                    
                    Platform.runLater(() -> {
                        if (modelSelector != null) {
                            String currentSelection = modelSelector.getSelectionModel().getSelectedItem();
                            modelSelector.getItems().clear();
                            
                            for (int i = 0; i < models.length(); i++) {
                                org.json.JSONObject model = models.getJSONObject(i);
                                String modelName = model.getString("name");
                                modelSelector.getItems().add(modelName);
                                // Very loose matching for loaded model selection
                                if (loaded && provider.toLowerCase().contains(modelName.toLowerCase().replace(".gguf", ""))) {
                                    currentSelection = modelName;
                                }
                            }
                            
                            // Add a browse option at the very bottom
                            modelSelector.getItems().add("📂 Browse Custom Model...");
                            
                            if (loaded) {
                                if (currentSelection != null) {
                                    modelSelector.getSelectionModel().select(currentSelection);
                                } else {
                                    modelSelector.setPromptText("✅ " + provider);
                                }
                                modelSelector.setStyle("-fx-text-fill: #43e97b; -fx-background-color: transparent; -fx-border-color: #555; -fx-border-radius: 12; -fx-font-size: 11px;");
                            } else {
                                modelSelector.setPromptText("⏳ Model pending...");
                                modelSelector.setStyle("-fx-text-fill: #ffd700; -fx-background-color: transparent; -fx-border-color: #555; -fx-border-radius: 12; -fx-font-size: 11px;");
                            }
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        if (modelSelector != null) {
                            modelSelector.setPromptText("🔴 Engine offline");
                            modelSelector.setStyle("-fx-text-fill: #f5576c; -fx-background-color: transparent; -fx-border-color: #555; -fx-border-radius: 12; -fx-font-size: 11px;");
                        }
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (modelSelector != null) {
                        modelSelector.setPromptText("🔴 Engine starting...");
                        modelSelector.setStyle("-fx-text-fill: #f5576c; -fx-background-color: transparent; -fx-border-color: #555; -fx-border-radius: 12; -fx-font-size: 11px;");
                    }
                });
            }
        });
    }

    @FXML
    private void handleModelSelection() {
        String selectedModel = modelSelector.getSelectionModel().getSelectedItem();
        if (selectedModel == null) return;
        
        if (selectedModel.equals("📂 Browse Custom Model...")) {
            // Restore previous selection temporarily while dialog is open
            Platform.runLater(() -> modelSelector.getSelectionModel().selectFirst());
            
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Select Local AI Model");
            fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("GGUF Models", "*.gguf"),
                new javafx.stage.FileChooser.ExtensionFilter("ONNX GenAI Folders", "*.*")
            );
            
            // Try to set initial directory to common download folders
            File userHome = new File(System.getProperty("user.home"));
            File downloads = new File(userHome, "Downloads");
            if (downloads.exists()) fileChooser.setInitialDirectory(downloads);
            
            File selectedFile = fileChooser.showOpenDialog(modelSelector.getScene().getWindow());
            if (selectedFile != null) {
                String path = selectedFile.getAbsolutePath();
                // If they selected a file inside an ONNX folder, use the folder instead
                if (path.toLowerCase().contains("genai_config") || path.toLowerCase().endsWith(".onnx") || path.toLowerCase().endsWith(".bin")) {
                    path = selectedFile.getParent();
                }
                final String finalPath = path;
                
                addSystemMessage("⏳ Loading custom model from: " + finalPath + "...");
                modelSelector.setStyle("-fx-text-fill: #ffd700; -fx-background-color: transparent; -fx-border-color: #555; -fx-border-radius: 12; -fx-font-size: 11px;");
                
                CompletableFuture.runAsync(() -> {
                    try {
                        aiOrchestrator.loadLocalModel(finalPath, "auto");
                        Platform.runLater(this::updateModelStatus);
                    } catch (Exception e) {
                        Platform.runLater(() -> addSystemMessage("❌ Failed to load custom model: " + e.getMessage()));
                    }
                });
            }
            return;
        }

        addSystemMessage("⏳ Switching to model: " + selectedModel + "...");
        modelSelector.setStyle("-fx-text-fill: #ffd700; -fx-background-color: transparent; -fx-border-color: #555; -fx-border-radius: 12; -fx-font-size: 11px;");
        
        CompletableFuture.runAsync(() -> {
                try {
                    org.json.JSONArray models = aiOrchestrator.listLocalModels();
                    String fullPath = null;
                    for (int i = 0; i < models.length(); i++) {
                        org.json.JSONObject m = models.getJSONObject(i);
                        if (m.getString("name").equals(selectedModel)) {
                            fullPath = m.getString("path");
                            break;
                        }
                    }
                    if (fullPath != null) {
                        aiOrchestrator.loadLocalModel(fullPath, "auto");
                        Platform.runLater(this::updateModelStatus);
                    } else {
                        Platform.runLater(() -> addSystemMessage("❌ Could not find path for model: " + selectedModel));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> addSystemMessage("❌ Failed to switch model: " + e.getMessage()));
                }
            });
    }

    private void ensureModelLoaded() {
        if (aiOrchestrator.isForceCloud()) {
            return;
        }
        if (modelAutoLoadTriggered)
            return;
        modelAutoLoadTriggered = true;

        CompletableFuture.runAsync(() -> {
            try {
                if (aiOrchestrator.isLocalAvailable()) {
                    org.json.JSONObject health = aiOrchestrator.getLocalHealth();
                    if (!health.optBoolean("model_loaded", false)) {
                        Platform.runLater(() -> addSystemMessage("⏳ Loading AI model in background..."));
                        aiOrchestrator.loadLocalModel();

                        // Poll until model is loaded, then update status
                        for (int i = 0; i < 60; i++) {
                            Thread.sleep(2000);
                            org.json.JSONObject polled = aiOrchestrator.getLocalHealth();
                            if (polled.optBoolean("model_loaded", false)) {
                                String provider = polled.optString("provider", "loaded");
                                Platform.runLater(() -> {
                                    addSystemMessage("✅ AI model ready (" + provider + ")");
                                    if (modelSelector != null) {
                                        modelSelector.setPromptText("✅ " + provider);
                                        modelSelector.setStyle("-fx-text-fill: #43e97b; -fx-background-color: transparent; -fx-border-color: #555; -fx-border-radius: 12; -fx-font-size: 11px;");
                                        updateModelStatus(); // Refresh dropdown items
                                    }
                                });
                                break;
                            }
                        }
                    } else {
                        Platform.runLater(this::updateModelStatus);
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
        if (stopButton != null) {
            stopButton.setVisible(true);
            stopButton.setManaged(true);
        }
        showTypingIndicator();

        boolean requiresPrecision = msg.toLowerCase().contains("dosage") ||
                msg.toLowerCase().contains("side effect") ||
                msg.toLowerCase().contains("interaction");

        currentRequest = aiOrchestrator.processQuery(msg, requiresPrecision, useSearch)
                .thenAccept(response -> Platform.runLater(() -> {
                    removeTypingIndicator();
                    addAIMessage(response);
                    sendButton.setDisable(false);
                    if (stopButton != null) {
                        stopButton.setVisible(false);
                        stopButton.setManaged(false);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        removeTypingIndicator();
                        sendButton.setDisable(false);
                        if (stopButton != null) {
                            stopButton.setVisible(false);
                            stopButton.setManaged(false);
                        }
                        if (!(ex instanceof CancellationException) && !(ex.getCause() instanceof CancellationException)) {
                            addSystemMessage("❌ Error: " + ex.getMessage());
                        }
                    });
                    return null;
                });
    }

    @FXML
    private void handleStopGeneration() {
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
            aiOrchestrator.cancelLocalGeneration();
            addSystemMessage("🛑 Generation stopped by user.");
            removeTypingIndicator();
            sendButton.setDisable(false);
            if (stopButton != null) {
                stopButton.setVisible(false);
                stopButton.setManaged(false);
            }
        }
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

        aiOrchestrator.queryDatabase(queryPrompt)
                .thenAccept(dbData -> Platform.runLater(() -> {
                    removeTypingIndicator();
                    // Phase 1: Show raw DB data instantly
                    addReportMessage(displayName, dbData);

                    // Phase 2: Send to AI for polished analysis
                    if (stopButton != null) {
                        stopButton.setVisible(true);
                        stopButton.setManaged(true);
                    }
                    showTypingIndicator();
                    
                    String analysisPrompt = "Analyze this report comprehensively. " +
                        (displayName.contains("Inventory") ? "Summarize key findings, total medicines, price range. Give 2-3 actionable recommendations." :
                        displayName.contains("Low Stock") ? "Which medicines need urgent reordering? Prioritize by criticality." :
                        displayName.contains("Expir") ? "Which should be discounted for quick sale? Which returned?" :
                        displayName.contains("Sales") ? "How is today's performance? Any insights or suggestions?" :
                        displayName.contains("Customer") ? "Who are the highest debtors? Suggest a follow-up strategy." :
                        "Provide a helpful summary with actionable insights.");

                    org.json.JSONObject data = new org.json.JSONObject()
                        .put("prompt", analysisPrompt)
                        .put("business_context", dbData);

                    currentRequest = aiOrchestrator.processOrchestration("raw_chat", data, "local_fallback", false)
                            .thenAccept(aiResponse -> Platform.runLater(() -> {
                                removeTypingIndicator();
                                addAIAnalysisMessage(aiResponse);
                                sendButton.setDisable(false);
                                if (stopButton != null) {
                                    stopButton.setVisible(false);
                                    stopButton.setManaged(false);
                                }
                            }))
                            .exceptionally(ex -> {
                                Platform.runLater(() -> {
                                    removeTypingIndicator();
                                    sendButton.setDisable(false);
                                    if (stopButton != null) {
                                        stopButton.setVisible(false);
                                        stopButton.setManaged(false);
                                    }
                                    if (!(ex instanceof CancellationException) && !(ex.getCause() instanceof CancellationException)) {
                                        addSystemMessage("💡 AI analysis unavailable — raw data shown above.");
                                    }
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

    // ======================== QUICK REPORT HANDLERS ========================

    @FXML
    private void handleInventoryReport() {
        sendDbQuery("Show inventory summary - list top medicines with stock quantities and prices", "📦 Inventory Summary");
    }

    @FXML
    private void handleLowStockReport() {
        sendDbQuery("Show low stock medicines that are running out", "⚠️ Low Stock Alert");
    }

    @FXML
    private void handleExpiryReport() {
        sendDbQuery("Show medicines expiring soon within the next 90 days", "⏰ Expiring Medicines");
    }

    @FXML
    private void handleSalesReport() {
        sendDbQuery("Show today's sales summary and revenue", "💰 Sales Report");
    }

    @FXML
    private void handleCustomerReport() {
        sendDbQuery("Show customer balances and outstanding debts", "👥 Customer Balances");
    }

    @FXML
    private void handleTopSellersReport() {
        sendDbQuery("Show top 20 best-selling medicines by total quantity sold from bill items", "🏆 Top Selling Medicines");
    }

    @FXML
    private void handleProfitReport() {
        sendDbQuery("Show profit analysis - total revenue, total bills, average bill value, and revenue by payment mode", "📊 Profit Analysis");
    }

    @FXML
    private void handlePrescriptionReport() {
        sendDbQuery("Show recent prescriptions with patient name, doctor, status, and medicines prescribed", "📋 Prescription Overview");
    }

    @FXML
    private void handleDrugInteractions() {
        sendDbQuery("Show recent bills with multiple medicines to check for potential drug-drug interactions. List patient and all medicines per bill", "💊 Drug Interaction Check");
    }

    @FXML
    private void handleReorderSuggestions() {
        sendDbQuery("Show medicines with stock below 20 units that have been sold recently - suggest reorder quantities based on past sales velocity", "🔄 Reorder Suggestions");
    }

    @FXML
    private void handleDailySummary() {
        sendDbQuery("Give a complete daily summary: total sales today, number of bills, new customers, pending prescriptions, low stock alerts, and expiring medicines count", "📅 Daily Summary");
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
        box.setPadding(new Insets(4, 30, 4, 10));

        Label avatar = new Label("🤖");
        avatar.setStyle("-fx-font-size: 18px; -fx-padding: 4 0 0 0;");

        VBox bubble = new VBox(2);
        bubble.setMaxWidth(550);
        bubble.setStyle(
                "-fx-background-color: #2a2d3e;" +
                        "-fx-padding: 8 12; -fx-background-radius: 16 16 16 4;");

        String htmlContent = HTML_RENDERER.render(MARKDOWN_PARSER.parse(text));
        String wrappedHtml = "<html><body style='background-color:#2a2d3e; color:#e0e0e0; font-family:-apple-system, Segoe UI, sans-serif; font-size:13px; line-height:1.4; margin:0; padding:0;'>"
                           + htmlContent + "</body></html>";

        WebView webView = new WebView();
        webView.setMaxWidth(530);
        webView.setPrefHeight(30);
        webView.getEngine().loadContent(wrappedHtml);

        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    try {
                        Object height = webView.getEngine().executeScript("document.documentElement.scrollHeight || document.body.scrollHeight");
                        if (height instanceof Number) {
                            webView.setPrefHeight(((Number) height).doubleValue() + 20);
                        }
                    } catch (Exception e) {}
                });
            }
        });

        Label timeLabel = new Label(timestamp());
        timeLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 9px;");

        bubble.getChildren().addAll(webView, timeLabel);
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
        String htmlContent = HTML_RENDERER.render(MARKDOWN_PARSER.parse(text));
        String wrappedHtml = "<html><body style='background-color:#1a1e2e; color:#d0d0d0; font-family:-apple-system, Segoe UI, sans-serif; font-size:12.5px; line-height:1.4; margin:0; padding:0;'>"
                           + htmlContent + "</body></html>";

        WebView webView = new WebView();
        webView.setMaxWidth(560);
        webView.setPrefHeight(30);
        webView.getEngine().loadContent(wrappedHtml);

        webView.getEngine().documentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (newDoc != null) {
                Platform.runLater(() -> {
                    try {
                        Object height = webView.getEngine().executeScript("document.documentElement.scrollHeight || document.body.scrollHeight");
                        if (height instanceof Number) {
                            webView.setPrefHeight(((Number) height).doubleValue() + 20);
                        }
                    } catch (Exception e) {}
                });
            }
        });

        // Timestamp
        Label timeLabel = new Label("🤖 AI-generated • " + timestamp());
        timeLabel.setStyle("-fx-text-fill: #a855f7; -fx-font-size: 9px; -fx-padding: 4 0 0 0;");

        card.getChildren().addAll(header, sep, webView, timeLabel);
        box.getChildren().addAll(avatar, card);
        chatBox.getChildren().add(box);
    }
}
