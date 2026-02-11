package org.example.MediManage;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.example.MediManage.service.ai.LocalAIService;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ModelStoreController {

    @FXML
    private TilePane modelsContainer;
    @FXML
    private ProgressBar globalProgressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private TextField searchField;

    private final LocalAIService aiService = new LocalAIService();
    private Timer progressTimer;

    @FXML
    public void initialize() {
        loadCuratedModels();
    }

    private void loadCuratedModels() {
        List<ModelCard> models = new ArrayList<>();

        // Curated List of ONNX Optimized Models
        models.add(new ModelCard("Microsoft Phi-3 Mini (CPU)", "microsoft/Phi-3-mini-4k-instruct-onnx",
                "cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/*",
                "High quality, 3.8B params. CPU Optimized.", "Recommended"));

        models.add(new ModelCard("Microsoft Phi-3 Mini (GPU)", "microsoft/Phi-3-mini-4k-instruct-onnx",
                "directml/*", // Simplified pattern
                "High quality, 3.8B params. RTX/DirectML Optimized.", "Powerful"));

        models.add(new ModelCard("TinyLlama 1.1B Chat", "Xenova/TinyLlama-1.1B-Chat-v1.0", null,
                "Fast, light (1.1B). Good for older laptops.", "Fastest"));

        models.add(new ModelCard("Qwen 1.5 (0.5B)", "Xenova/Qwen1.5-0.5B-Chat", null,
                "Extremely fast, ultra-low memory usage.", "Lightweight"));

        // Render
        renderModels(models);
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            loadCuratedModels();
            return;
        }

        // In a real app, this would query Hugging Face API
        // For now, we filter local list or show a "Search HF" card
        statusLabel.setText("Searching Hugging Face for '" + query + "'... (Simulated)");
        // TODO: Implement real HF Search API if needed
    }

    private void renderModels(List<ModelCard> models) {
        modelsContainer.getChildren().clear();
        for (ModelCard model : models) {
            modelsContainer.getChildren().add(createModelCard(model));
        }
    }

    private VBox createModelCard(ModelCard model) {
        VBox card = new VBox(10);
        card.setStyle(
                "-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0);");
        card.setPrefWidth(250);
        card.setMinWidth(250);

        Label badge = new Label(model.badge);
        badge.setStyle(
                "-fx-background-color: #e3f2fd; -fx-text-fill: #1976d2; -fx-padding: 3 8; -fx-background-radius: 5; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label title = new Label(model.name);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333;");

        Label desc = new Label(model.description);
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        Button downloadBtn = new Button("Download");
        downloadBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand;");
        downloadBtn.setMaxWidth(Double.MAX_VALUE);
        downloadBtn.setOnAction(e -> startDownload(model));

        card.getChildren().addAll(badge, title, desc, downloadBtn);
        return card;
    }

    private void startDownload(ModelCard model) {
        statusLabel.setText("Starting download for " + model.name + "...");
        globalProgressBar.setProgress(-1); // Indeterminate

        // Call Backend
        aiService.startDownload(model.repoId, model.filename);

        // Start Polling
        startProgressPolling();
    }

    private void startProgressPolling() {
        if (progressTimer != null)
            progressTimer.cancel();

        progressTimer = new Timer();
        progressTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                JSONObject status = aiService.getDownloadStatus();
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

        if ("completed".equals(state)) {
            progressTimer.cancel();
            statusLabel.setText("Download Complete! You can now select this model in Settings.");
            globalProgressBar.setProgress(1.0);
            showAlert("Success", "Model downloaded successfully!\nLocation: " + status.optString("path"));
        } else if ("error".equals(state)) {
            progressTimer.cancel();
            globalProgressBar.setProgress(0);
            showAlert("Error", "Download failed: " + message);
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    // record class equivalent
    private static class ModelCard {
        String name;
        String repoId;
        String filename; // Optional
        String description;
        String badge;

        public ModelCard(String name, String repoId, String filename, String description, String badge) {
            this.name = name;
            this.repoId = repoId;
            this.filename = filename;
            this.description = description;
            this.badge = badge;
        }
    }
}
