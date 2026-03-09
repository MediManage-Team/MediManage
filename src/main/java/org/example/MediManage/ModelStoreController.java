package org.example.MediManage;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.util.AppExecutors;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ModelStoreController {

    // Download Tab
    @FXML
    private VBox modelsContainer;
    @FXML
    private TextField searchField;

    // Installed Tab
    @FXML
    private VBox installedModelsContainer;
    @FXML
    private Label modelsDirLabel;

    // Shared
    @FXML
    private ProgressBar globalProgressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private TabPane mainTabPane;

    private final AIOrchestrator aiOrchestrator = AIServiceProvider.get().getOrchestrator();
    private Timer progressTimer;
    private Button stopButton;
    private final List<ModelCard> curatedModels = new ArrayList<>();
    private static final String MODELS_DIR = System.getProperty("user.home") + "/MediManage/models";
    private boolean isDownloading = false;

    @FXML
    public void initialize() {
        loadCuratedModels();
        if (modelsDirLabel != null) {
            modelsDirLabel.setText(MODELS_DIR);
        }
        // Create Stop button programmatically and add to bottom bar
        if (statusLabel != null && statusLabel.getParent() instanceof VBox bottomBar) {
            stopButton = new Button("⏹ Stop Download");
            stopButton.getStyleClass().add("button-danger");
            stopButton.setStyle("-fx-font-weight: bold;");
            stopButton.setVisible(false);
            stopButton.setManaged(false);
            stopButton.setOnAction(e -> handleStopDownload());
            bottomBar.getChildren().add(1, stopButton); // Insert after statusLabel
        }
        // Load installed models on init
        Platform.runLater(this::loadInstalledModels);

        // Add live-search behavior
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                handleSearch();
            });
        }
    }

    // ======================== DOWNLOAD TAB ========================

    private void loadCuratedModels() {
        // ══════════ NVIDIA GPU (CUDA) ══════════
        List<ModelCard> gpuModels = new ArrayList<>();
        gpuModels.add(new ModelCard("Microsoft Phi-4 Mini (GPU)", "microsoft/Phi-4-mini-instruct-onnx",
                "gpu/gpu-int4-rtn-block-32/*",
                "Phi-4 Mini with CUDA GPU acceleration. RTX 2060+.", "\u2B50 Best", "ONNX GenAI",
                "huggingface", "~2.4 GB", "gpu"));

        gpuModels.add(new ModelCard("Mistral 7B v0.3", "mistral:7b", null,
                "Mistral 7B. Premium quality, needs 6GB+ VRAM.", "Premium", "GGUF", "ollama",
                "~4.1 GB", "gpu"));

        gpuModels.add(new ModelCard("DeepSeek-R1 7B", "deepseek-r1:7b", null,
                "DeepSeek R1 7B. Advanced reasoning, 6GB+ VRAM.", "Premium", "GGUF", "ollama",
                "~4.7 GB", "gpu"));

        // ══════════ CPU (BitNet.cpp / llama.cpp) ══════════
        List<ModelCard> cpuModels = new ArrayList<>();

        cpuModels.add(new ModelCard("Phi-4 Mini (CPU ONNX)", "microsoft/Phi-4-mini-instruct-onnx",
                "cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/*",
                "Phi-4 Mini 3.8B. Best quality-per-param. ONNX CPU optimized.", "\u2B50 Best", "ONNX GenAI",
                "huggingface", "~2.4 GB", "cpu"));

        cpuModels.add(new ModelCard("Llama 3.2 3B", "llama3.2:3b", null,
                "Meta's compact model. Strong reasoning, 3B params.", "Recommended", "GGUF", "ollama",
                "~2.0 GB", "cpu"));

        cpuModels.add(new ModelCard("Llama 3.2 1B", "llama3.2:1b", null,
                "Ultra-fast 1B model by Meta. Great for quick tasks.", "Fast", "GGUF", "ollama",
                "~1.3 GB", "cpu"));

        cpuModels.add(new ModelCard("Qwen2.5 3B", "qwen2.5:3b", null,
                "Alibaba Qwen2.5 3B. Multilingual + coding.", "Recommended", "GGUF", "ollama",
                "~1.9 GB", "cpu"));

        cpuModels.add(new ModelCard("Qwen2.5 1.5B", "qwen2.5:1.5b", null,
                "Qwen2.5 1.5B. Fast responses, solid quality.", "Fast", "GGUF", "ollama",
                "~986 MB", "cpu"));

        cpuModels.add(new ModelCard("Qwen2.5 0.5B", "qwen2.5:0.5b", null,
                "Smallest Qwen2.5. Runs on anything.", "Lightweight", "GGUF", "ollama",
                "~394 MB", "cpu"));

        cpuModels.add(new ModelCard("Gemma 3 4B", "gemma3:4b", null,
                "Google Gemma 3 4B. Strong reasoning + medical.", "Recommended", "GGUF", "ollama",
                "~3.3 GB", "cpu"));

        cpuModels.add(new ModelCard("Gemma 3 1B", "gemma3:1b", null,
                "Google Gemma 3 1B. Lightweight but capable.", "Lightweight", "GGUF", "ollama",
                "~815 MB", "cpu"));

        cpuModels.add(new ModelCard("Phi-4 Mini 3.8B", "phi4-mini", null,
                "Microsoft Phi-4 Mini via Ollama. Top-tier.", "\u2B50 Best", "GGUF", "ollama",
                "~2.5 GB", "cpu"));

        cpuModels.add(new ModelCard("DeepSeek-R1 1.5B", "deepseek-r1:1.5b", null,
                "DeepSeek R1 1.5B. Chain-of-thought reasoning.", "New", "GGUF", "ollama",
                "~1.1 GB", "cpu"));

        // Render all sections
        curatedModels.clear();
        curatedModels.addAll(gpuModels);
        curatedModels.addAll(cpuModels);
        renderHardwareSections(gpuModels, cpuModels);
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            loadCuratedModels();
            statusLabel.setText("Showing curated model catalog.");
            return;
        }

        if (curatedModels.isEmpty()) {
            loadCuratedModels();
        }

        List<ModelCard> filtered = filterCuratedModels(query);
        if (filtered.isEmpty()) {
            modelsContainer.getChildren().clear();
            Label empty = new Label("No curated models matched \"" + query
                    + "\".\nTry model name, repository, hardware, format, or badge.");
            empty.setWrapText(true);
            empty.getStyleClass().add("text-muted-italic");
            modelsContainer.getChildren().add(empty);
            statusLabel.setText("No matches for '" + query + "'.");
            return;
        }

        renderDownloadModels(filtered);
        statusLabel.setText("Found " + filtered.size() + " model(s) for '" + query + "'.");
    }

    private List<ModelCard> filterCuratedModels(String query) {
        String lowerQuery = query.toLowerCase();
        List<ModelCard> filtered = new ArrayList<>();
        for (ModelCard model : curatedModels) {
            if (modelMatchesQuery(model, lowerQuery)) {
                filtered.add(model);
            }
        }
        return filtered;
    }

    private boolean modelMatchesQuery(ModelCard model, String lowerQuery) {
        return model.name.toLowerCase().contains(lowerQuery)
                || model.repoId.toLowerCase().contains(lowerQuery)
                || model.description.toLowerCase().contains(lowerQuery)
                || model.badge.toLowerCase().contains(lowerQuery)
                || model.format.toLowerCase().contains(lowerQuery)
                || model.hardware.toLowerCase().contains(lowerQuery)
                || model.source.toLowerCase().contains(lowerQuery);
    }

    private void renderHardwareSections(List<ModelCard> gpu, List<ModelCard> cpu) {
        modelsContainer.getChildren().clear();

        addSection("\uD83D\uDFE2  NVIDIA GPU (CUDA)",
                "onnxruntime-gpu  \u2022  Requires NVIDIA GPU with 4GB+ VRAM",
                "#5fe6b3", "#0f2920", gpu);

        addSection("\uD83D\uDFE0  CPU (BitNet.cpp / llama.cpp)",
                "llama-cpp-python  \u2022  Any CPU, no GPU required",
                "#e8c66a", "#1a1a0f", cpu);
    }

    private void addSection(String title, String subtitle, String textColor, String bgColor,
            List<ModelCard> models) {
        if (models.isEmpty())
            return;

        // Section header
        VBox header = new VBox(3);
        header.setStyle("-fx-background-color: " + bgColor + "; -fx-padding: 12 15; -fx-background-radius: 8;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.7;");

        header.getChildren().addAll(titleLabel, subtitleLabel);
        modelsContainer.getChildren().add(header);

        // Model cards in a TilePane
        TilePane tilePane = new TilePane();
        tilePane.setHgap(15);
        tilePane.setVgap(15);
        tilePane.setPrefColumns(4);
        for (ModelCard model : models) {
            tilePane.getChildren().add(createDownloadCard(model));
        }
        modelsContainer.getChildren().add(tilePane);
    }

    private void renderDownloadModels(List<ModelCard> models) {
        modelsContainer.getChildren().clear();
        TilePane tilePane = new TilePane();
        tilePane.setHgap(15);
        tilePane.setVgap(15);
        for (ModelCard model : models) {
            tilePane.getChildren().add(createDownloadCard(model));
        }
        modelsContainer.getChildren().add(tilePane);
    }

    private VBox createDownloadCard(ModelCard model) {
        VBox card = new VBox(8);
        card.setStyle(
                "-fx-background-color: #0f1724; -fx-padding: 15; -fx-background-radius: 10; " +
                        "-fx-border-color: #2d3555; -fx-border-radius: 10; -fx-border-width: 1;");
        card.setPrefWidth(210);
        card.setMinWidth(210);

        // Badge row
        HBox badgeRow = new HBox(5);
        Label badge = new Label(model.badge);
        String badgeColor;
        switch (model.hardware) {
            case "gpu":
                badgeColor = "#0f2920; -fx-text-fill: #5fe6b3";
                break;
            default:
                badgeColor = "#1a1a0f; -fx-text-fill: #e8c66a";
                break;
        }
        badge.setStyle(
                "-fx-background-color: " + badgeColor + "; " +
                        "-fx-padding: 3 8; -fx-background-radius: 5; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label formatBadge = new Label(model.format);
        formatBadge.setStyle(
                "-fx-background-color: #200f20; -fx-text-fill: #ff6b6b; " +
                        "-fx-padding: 3 8; -fx-background-radius: 5; -fx-font-size: 10px; -fx-font-weight: bold;");
        badgeRow.getChildren().addAll(badge, formatBadge);

        Label title = new Label(model.name);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e6f0ff;");

        Label desc = new Label(model.description);
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #bfc9e6; -fx-font-size: 11px;");
        desc.setMaxHeight(40);

        // Size + repo row
        HBox infoRow = new HBox(8);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        Label sizeLabel = new Label("\uD83D\uDCBE " + model.sizeInfo);
        sizeLabel.setStyle("-fx-text-fill: #00d4ff; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label repoLabel = new Label(model.repoId);
        repoLabel.setStyle("-fx-text-fill: #4e4b6c; -fx-font-size: 9px;");
        infoRow.getChildren().addAll(sizeLabel, repoLabel);

        Button downloadBtn = new Button("\u2B07 Download");
        downloadBtn.setStyle(
                "-fx-background-color: #5fe6b3; -fx-text-fill: #061427; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 12px;");
        downloadBtn.setMaxWidth(Double.MAX_VALUE);
        downloadBtn.setOnAction(e -> startDownload(model));

        card.getChildren().addAll(badgeRow, title, desc, infoRow, downloadBtn);

        // Add smooth hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: #1a2235; -fx-padding: 15; -fx-background-radius: 10; -fx-border-color: #3b466e; -fx-border-radius: 10; -fx-border-width: 1;");
            card.setScaleX(1.02);
            card.setScaleY(1.02);
        });
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: #0f1724; -fx-padding: 15; -fx-background-radius: 10; -fx-border-color: #2d3555; -fx-border-radius: 10; -fx-border-width: 1;");
            card.setScaleX(1.0);
            card.setScaleY(1.0);
        });

        return card;
    }

    private void startDownload(ModelCard model) {
        if (isDownloading) {
            org.example.MediManage.util.ToastNotification.warning("A download is already in progress!");
            return;
        }
        isDownloading = true;
        statusLabel.setText("Starting download for " + model.name + " (via " + model.source + ")...");
        globalProgressBar.setProgress(-1); // Indeterminate
        showStopButton(true);

        aiOrchestrator.startModelDownload(model.repoId, model.filename, model.source);
        startProgressPolling();
    }

    private void startProgressPolling() {
        if (progressTimer != null)
            progressTimer.cancel();

        progressTimer = new Timer(true); // Daemon timer
        progressTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                JSONObject status = aiOrchestrator.getModelDownloadStatus();
                Platform.runLater(() -> updateProgress(status));
            }
        }, 1000, 1000);
    }

    private void updateProgress(JSONObject status) {
        String state = status.optString("status", "error");
        String message = status.optString("message", "");
        double percent = status.optDouble("percent", 0);

        statusLabel.setText(message);
        globalProgressBar.setProgress(percent / 100.0);

        if ("verifying".equals(state)) {
            statusLabel.setText("🔒 Verifying checksums...");
            globalProgressBar.setProgress(-1); // Indeterminate during verification
        } else if ("completed".equals(state)) {
            isDownloading = false;
            progressTimer.cancel();
            statusLabel.setText("✅ " + message);
            globalProgressBar.setProgress(1.0);
            showStopButton(false);

            // Auto-refresh installed models tab
            loadInstalledModels();

            showAlert("Success", "Model downloaded & verified!\nLocation: " + status.optString("path"));
            org.example.MediManage.util.ToastNotification.success("Model downloaded successfully");
        } else if ("cancelled".equals(state)) {
            isDownloading = false;
            progressTimer.cancel();
            statusLabel.setText("⏹ Download cancelled.");
            org.example.MediManage.util.ToastNotification.warning("Download cancelled");
            globalProgressBar.setProgress(0);
            showStopButton(false);
        } else if ("error".equals(state)) {
            isDownloading = false;
            progressTimer.cancel();
            globalProgressBar.setProgress(0);
            showStopButton(false);
            showAlert("Error", "Download failed: " + message);
            org.example.MediManage.util.ToastNotification.error("Download failed: " + message);
        }
    }

    // ======================== INSTALLED MODELS TAB ========================

    @FXML
    private void handleRefreshInstalled() {
        loadInstalledModels();
    }

    @FXML
    private void handleOpenModelsFolder() {
        try {
            File dir = new File(MODELS_DIR);
            dir.mkdirs();
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            showAlert("Error", "Could not open folder: " + e.getMessage());
        }
    }

    private void loadInstalledModels() {
        if (installedModelsContainer == null)
            return;
        installedModelsContainer.getChildren().clear();

        AppExecutors.runBackground(() -> {
            JSONArray models = aiOrchestrator.listLocalModels();
            Platform.runLater(() -> {
                if (models.length() == 0) {
                    Label empty = new Label("No models installed yet. Download one from the 'Download Models' tab.");
                    empty.setStyle("-fx-text-fill: #4e4b6c; -fx-font-size: 14px; -fx-padding: 40;");
                    installedModelsContainer.getChildren().add(empty);
                    return;
                }

                for (int i = 0; i < models.length(); i++) {
                    JSONObject model = models.getJSONObject(i);
                    installedModelsContainer.getChildren().add(createInstalledModelCard(model));
                }
            });
        });
    }

    private HBox createInstalledModelCard(JSONObject model) {
        HBox card = new HBox(15);
        card.setStyle(
                "-fx-background-color: #0f1724; -fx-padding: 15; -fx-background-radius: 8; " +
                        "-fx-border-color: #2d3555; -fx-border-radius: 8; -fx-border-width: 1;");
        card.setAlignment(Pos.CENTER_LEFT);

        // Model info
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label name = new Label(model.optString("name", "Unknown"));
        name.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #e6f0ff;");

        HBox metaRow = new HBox(10);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        String format = model.optString("format", "unknown");
        Label formatBadge = new Label(format);
        String badgeColor = getBadgeColor(format);
        formatBadge.setStyle(
                "-fx-background-color: " + badgeColor + "; -fx-text-fill: white; " +
                        "-fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;");

        double sizeMb = model.optDouble("size_mb", 0);
        String sizeStr = sizeMb > 1024 ? String.format("%.1f GB", sizeMb / 1024) : String.format("%.0f MB", sizeMb);
        Label sizeLabel = new Label(sizeStr);
        sizeLabel.setStyle("-fx-text-fill: #4e4b6c; -fx-font-size: 11px;");

        Label pathLabel = new Label(model.optString("path", ""));
        pathLabel.setStyle("-fx-text-fill: #4e4b6c; -fx-font-size: 10px;");
        pathLabel.setMaxWidth(400);

        metaRow.getChildren().addAll(formatBadge, sizeLabel);
        info.getChildren().addAll(name, metaRow, pathLabel);

        // Action buttons
        VBox actions = new VBox(5);
        actions.setAlignment(Pos.CENTER);

        Button loadBtn = new Button("▶ Load");
        loadBtn.setStyle(
                "-fx-background-color: #00d4ff; -fx-text-fill: #061427; -fx-cursor: hand; -fx-font-size: 11px;");
        loadBtn.setPrefWidth(80);
        loadBtn.setOnAction(e -> handleLoadModel(model));

        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.setStyle(
                "-fx-background-color: #ff6b6b30; -fx-text-fill: #ff6b6b; -fx-cursor: hand; -fx-font-size: 11px;");
        deleteBtn.setPrefWidth(80);
        deleteBtn.setOnAction(e -> handleDeleteModel(model));

        actions.getChildren().addAll(loadBtn, deleteBtn);

        card.getChildren().addAll(info, actions);

        // Add subtle hover effect for installed list row
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: #1a2235; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #3b466e; -fx-border-radius: 8; -fx-border-width: 1;");
            card.setScaleX(1.005);
            card.setScaleY(1.005);
        });
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: #0f1724; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2d3555; -fx-border-radius: 8; -fx-border-width: 1;");
            card.setScaleX(1.0);
            card.setScaleY(1.0);
        });

        return card;
    }

    private String getBadgeColor(String format) {
        switch (format) {
            case "ONNX GenAI":
                return "#1565C0";
            case "OpenVINO":
                return "#FF6F00";
            case "ONNX":
                return "#2E7D32";
            case "GGUF":
                return "#6A1B9A";
            default:
                return "#757575";
        }
    }

    private void handleLoadModel(JSONObject model) {
        String path = model.optString("path", "");
        if (path.isEmpty())
            return;

        String name = model.optString("name");
        statusLabel.setText("Loading model: " + name + "...");
        aiOrchestrator.loadLocalModel(path, "auto");
        
        // Persist the choice so Settings and reboots remember it
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                .userNodeForPackage(org.example.MediManage.SettingsController.class);
        prefs.put("local_model_path", path);

        statusLabel.setText("✅ Model set as active: " + name);
        org.example.MediManage.util.ToastNotification.info("Model set as active: " + name);
    }

    private void handleDeleteModel(JSONObject model) {
        String name = model.optString("name", "Unknown");
        String path = model.optString("path", "");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Model");
        confirm.setHeaderText("Delete " + name + "?");
        confirm.setContentText("This will permanently delete the model from disk.\nPath: " + path);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                boolean deleted = aiOrchestrator.deleteLocalModel(path);
                if (deleted) {
                    statusLabel.setText("✅ Deleted: " + name);
                    org.example.MediManage.util.ToastNotification.success("Model deleted: " + name);
                    loadInstalledModels(); // Refresh
                } else {
                    showAlert("Error", "Failed to delete model. It may be in use or protected.");
                    org.example.MediManage.util.ToastNotification.error("Failed to delete model");
                }
            }
        });
    }

    // ======================== UTILITIES ========================

    private void showStopButton(boolean show) {
        if (stopButton != null) {
            stopButton.setVisible(show);
            stopButton.setManaged(show);
        }
    }

    private void handleStopDownload() {
        aiOrchestrator.stopModelDownload();
        statusLabel.setText("Cancelling download...");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    // Inner model data class
    private static class ModelCard {
        String name;
        String repoId;
        String filename;
        String description;
        String badge;
        String format;
        String source; // "huggingface", "ollama", "url"
        String sizeInfo; // e.g. "~2.4 GB"
        String hardware; // "gpu" or "cpu"

        public ModelCard(String name, String repoId, String filename, String description, String badge, String format,
                String source, String sizeInfo, String hardware) {
            this.name = name;
            this.repoId = repoId;
            this.filename = filename;
            this.description = description;
            this.badge = badge;
            this.format = format;
            this.source = source;
            this.sizeInfo = sizeInfo;
            this.hardware = hardware;
        }
    }
}
