package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.prefs.Preferences;
import org.json.JSONObject;

import org.example.MediManage.service.ai.CloudAIService;
import org.example.MediManage.service.ai.AIServiceProvider;

public class SettingsController {

    // --- Local AI ---
    @FXML
    private TextField modelPathField;
    @FXML
    private ComboBox<String> hardwareCombo;

    // --- Cloud AI ---
    @FXML
    private ComboBox<String> providerCombo;
    @FXML
    private ComboBox<String> modelCombo;
    @FXML
    private PasswordField geminiKeyField;
    @FXML
    private PasswordField groqKeyField;
    @FXML
    private PasswordField openrouterKeyField;
    @FXML
    private PasswordField openaiKeyField;
    @FXML
    private PasswordField claudeKeyField;

    // --- Environments ---
    @FXML
    private javafx.scene.layout.VBox envContainer;
    @FXML
    private javafx.scene.control.TextArea logConsole;

    private final org.example.MediManage.service.ai.PythonEnvironmentManager envManager = AIServiceProvider
            .get().getEnvManager();

    private Preferences prefs;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(SettingsController.class);

        // --- Local AI ---
        modelPathField.setText(prefs.get("local_model_path", ""));

        hardwareCombo.getItems().addAll("Auto", "OpenVINO (Intel NPU)", "Ryzen AI / DirectML (AMD)",
                "CUDA (NVIDIA GPU)", "CPU Only");
        hardwareCombo.setValue(prefs.get("ai_hardware", "Auto"));

        // --- Cloud AI ---
        // Provider ComboBox
        for (CloudAIService.Provider p : CloudAIService.getProviders()) {
            providerCombo.getItems().add(p.name());
        }
        String savedProvider = prefs.get("cloud_provider", "GEMINI");
        providerCombo.setValue(savedProvider);

        // Model ComboBox — populate based on current provider
        populateModels(savedProvider);

        // Provider change listener
        providerCombo.setOnAction(e -> {
            String selected = providerCombo.getValue();
            if (selected != null) {
                populateModels(selected);
            }
        });

        // API Keys
        geminiKeyField.setText(prefs.get("cloud_api_key", ""));
        groqKeyField.setText(prefs.get("groq_api_key", ""));
        openrouterKeyField.setText(prefs.get("openrouter_api_key", ""));
        openaiKeyField.setText(prefs.get("openai_api_key", ""));
        claudeKeyField.setText(prefs.get("claude_api_key", ""));

        // Environment list
        setupLogStreaming();
        loadEnvironmentList();
    }

    private void populateModels(String providerName) {
        modelCombo.getItems().clear();
        try {
            CloudAIService.Provider provider = CloudAIService.Provider.valueOf(providerName);
            String savedModel = prefs.get("cloud_model", "");
            boolean foundSaved = false;

            for (CloudAIService.ModelInfo model : CloudAIService.getModels(provider)) {
                modelCombo.getItems().add(model.toString()); // "ModelName [FREE]" or "ModelName [PAID]"
                if (model.id().equals(savedModel)) {
                    modelCombo.setValue(model.toString());
                    foundSaved = true;
                }
            }

            if (!foundSaved && !modelCombo.getItems().isEmpty()) {
                modelCombo.setValue(modelCombo.getItems().get(0));
            }
        } catch (Exception e) {
            // Invalid provider — leave blank
        }
    }

    private String getSelectedModelId() {
        String providerStr = providerCombo.getValue();
        String modelDisplay = modelCombo.getValue();
        if (providerStr == null || modelDisplay == null)
            return null;

        try {
            CloudAIService.Provider provider = CloudAIService.Provider.valueOf(providerStr);
            for (CloudAIService.ModelInfo model : CloudAIService.getModels(provider)) {
                if (model.toString().equals(modelDisplay)) {
                    return model.id();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // --- Environment Management (unchanged) ---

    private void setupLogStreaming() {
        envManager.setLogCallback(msg -> {
            javafx.application.Platform.runLater(() -> {
                if (logConsole != null) {
                    logConsole.appendText(msg + "\n");
                }
            });
        });
    }

    private void loadEnvironmentList() {
        envContainer.getChildren().clear();
        String activeEnv = prefs.get("active_python_env", "cpu");
        envManager.setActiveEnvironment(activeEnv);

        for (java.util.Map<String, Object> env : envManager.listEnvironments()) {
            envContainer.getChildren().add(createEnvCard(env, activeEnv));
        }
    }

    private javafx.scene.layout.HBox createEnvCard(java.util.Map<String, Object> env, String activeEnvName) {
        String name = (String) env.get("name");
        String label = (String) env.get("label");
        boolean installed = (Boolean) env.get("installed");
        boolean isActive = name.equals(activeEnvName);

        javafx.scene.layout.HBox card = new javafx.scene.layout.HBox(10);
        card.setStyle(
                "-fx-background-color: #0f1724; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #2d3555; -fx-border-radius: 8;");
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.layout.VBox infoBox = new javafx.scene.layout.VBox(3);
        javafx.scene.control.Label nameLabel = new javafx.scene.control.Label(label);
        nameLabel.setStyle("-fx-font-weight: bold;");

        javafx.scene.control.Label statusLabel = new javafx.scene.control.Label(
                installed ? "Installed" : "Not Installed");
        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (installed ? "#5fe6b3" : "#4e4b6c"));

        infoBox.getChildren().addAll(nameLabel, statusLabel);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        card.getChildren().addAll(infoBox, spacer);

        if (installed) {
            if (!isActive) {
                javafx.scene.control.Button activateBtn = new javafx.scene.control.Button("Set Active");
                activateBtn.setStyle("-fx-background-color: #00d4ff; -fx-text-fill: #061427; -fx-font-size: 10px;");
                activateBtn.setOnAction(e -> handleActivateEnv(name));
                card.getChildren().add(activateBtn);
            } else {
                javafx.scene.control.Label activeLabel = new javafx.scene.control.Label("ACTIVE");
                activeLabel.setStyle(
                        "-fx-background-color: #0f2920; -fx-text-fill: #5fe6b3; -fx-padding: 3 8; -fx-background-radius: 5; -fx-font-weight: bold; -fx-font-size: 10px;");
                card.getChildren().add(activeLabel);
            }

            javafx.scene.control.Button updateBtn = new javafx.scene.control.Button("Update");
            updateBtn.setStyle("-fx-background-color: #e8c66a; -fx-text-fill: #061427; -fx-font-size: 10px;");
            updateBtn.setOnAction(e -> handleInstallEnv(name));
            card.getChildren().add(updateBtn);

            javafx.scene.control.Button deleteBtn = new javafx.scene.control.Button("🗑");
            deleteBtn.setStyle("-fx-background-color: #ff6b6b30; -fx-text-fill: #ff6b6b; -fx-font-size: 10px;");
            deleteBtn.setOnAction(e -> handleDeleteEnv(name));
            card.getChildren().add(deleteBtn);
        } else {
            javafx.scene.control.Button installBtn = new javafx.scene.control.Button("Install");
            installBtn.setStyle("-fx-background-color: #5fe6b3; -fx-text-fill: #061427; -fx-font-size: 10px;");
            installBtn.setOnAction(e -> handleInstallEnv(name));
            card.getChildren().add(installBtn);
        }

        return card;
    }

    private void handleInstallEnv(String envName) {
        StartupProgressController popup = StartupProgressController.show();
        if (popup != null) {
            popup.setStatus("📦 Installing " + org.example.MediManage.service.ai.PythonEnvironmentManager.ENV_LABELS
                    .getOrDefault(envName, envName) + "...");
            popup.appendLog("⏳ Setting up '" + envName + "' environment. This may take a few minutes.");
            popup.appendLog("");
        }

        new Thread(() -> {
            try {
                envManager.setLogCallback(msg -> {
                    if (popup != null && !popup.isClosed()) {
                        popup.appendLog(msg);
                    }
                });

                envManager.ensureEnvironment(envName);

                if (popup != null && !popup.isClosed()) {
                    popup.setStatus("✅ Environment '" + envName + "' ready!");
                    popup.setProgress(1.0);
                    popup.appendLog("");
                    popup.appendLog("✅ Installation complete! This window will close shortly.");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                    }
                    popup.close();
                }

                javafx.application.Platform.runLater(() -> {
                    loadEnvironmentList();
                    setupLogStreaming();
                });
            } catch (Exception e) {
                e.printStackTrace();
                if (popup != null && !popup.isClosed()) {
                    popup.appendLog("❌ Error: " + e.getMessage());
                    popup.setStatus("❌ Installation failed");
                }
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error",
                        "Failed to install environment: " + e.getMessage()));
            }
        }).start();
    }

    private void handleDeleteEnv(String envName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + envName + "' environment?",
                javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.YES) {
                if (envManager.deleteEnvironment(envName)) {
                    loadEnvironmentList();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete environment.");
                }
            }
        });
    }

    private void handleActivateEnv(String envName) {
        prefs.put("active_python_env", envName);
        envManager.setActiveEnvironment(envName);
        loadEnvironmentList();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "'" + envName + "' is now active.\nRestart AI Engine now to apply?",
                javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
        confirm.setTitle("Environment Activated");
        confirm.setHeaderText("Restart AI Engine?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.YES) {
                MediManageApplication app = MediManageApplication.getInstance();
                if (app != null) {
                    if (logConsole != null)
                        logConsole.appendText("🔄 Restarting AI Engine with '" + envName + "'...\n");
                    app.restartServer();
                    scheduleModelReload();
                }
            }
        });
    }

    // --- Browse / Model Store ---

    @FXML
    private void handleBrowseModel() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Model Directory (ONNX GenAI / HuggingFace)");

        String modelsDir = System.getProperty("user.home") + "/MediManage/models";
        File modelsFolder = new File(modelsDir);
        if (modelsFolder.exists()) {
            dirChooser.setInitialDirectory(modelsFolder);
        }

        File selectedDir = dirChooser.showDialog(modelPathField.getScene().getWindow());
        if (selectedDir != null) {
            modelPathField.setText(selectedDir.getAbsolutePath());
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Model File (.onnx / .xml)");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("AI Models", "*.onnx", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        if (modelsFolder.exists()) {
            fileChooser.setInitialDirectory(modelsFolder);
        }
        File selectedFile = fileChooser.showOpenDialog(modelPathField.getScene().getWindow());
        if (selectedFile != null) {
            modelPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleOpenModelStore() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/org/example/MediManage/model-store-view.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("AI Model Store");
            stage.setScene(new javafx.scene.Scene(root, 900, 650));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open Model Store: " + e.getMessage());
        }
    }

    // --- Save Settings ---

    @FXML
    private void handleSaveSettings() {
        // Local AI
        prefs.put("local_model_path", modelPathField.getText());
        prefs.put("ai_hardware", hardwareCombo.getValue());

        // Cloud AI — Provider & Model
        String provider = providerCombo.getValue();
        String modelId = getSelectedModelId();
        if (provider != null)
            prefs.put("cloud_provider", provider);
        if (modelId != null)
            prefs.put("cloud_model", modelId);

        // Cloud AI — API Keys
        prefs.put("cloud_api_key", geminiKeyField.getText());
        prefs.put("groq_api_key", groqKeyField.getText());
        prefs.put("openrouter_api_key", openrouterKeyField.getText());
        prefs.put("openai_api_key", openaiKeyField.getText());
        prefs.put("claude_api_key", claudeKeyField.getText());

        // Update the live CloudAIService via AIServiceProvider
        try {
            CloudAIService cloud = AIServiceProvider.get().getCloudService();
            CloudAIService.Provider p = CloudAIService.Provider.valueOf(provider);

            // Set all keys
            cloud.setApiKey(CloudAIService.Provider.GEMINI, geminiKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.GROQ, groqKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.OPENROUTER, openrouterKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.OPENAI, openaiKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.CLAUDE, claudeKeyField.getText());

            // Set active provider + model
            String activeKey = switch (p) {
                case GEMINI -> geminiKeyField.getText();
                case GROQ -> groqKeyField.getText();
                case OPENROUTER -> openrouterKeyField.getText();
                case OPENAI -> openaiKeyField.getText();
                case CLAUDE -> claudeKeyField.getText();
            };
            cloud.configure(p, modelId != null ? modelId : "", activeKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        showAlert(Alert.AlertType.INFORMATION, "Settings Saved",
                "AI Configuration saved.\nCloud AI: " + provider + " / " + (modelId != null ? modelId : "default"));

        // Trigger local model reload if path set
        triggerModelReload();

        // Sync the active Python env to match the hardware selection
        String hardware = hardwareCombo.getValue();
        String config = "auto";
        if (hardware.contains("OpenVINO"))
            config = "openvino";
        else if (hardware.contains("DirectML") || hardware.contains("Ryzen"))
            config = "directml";
        else if (hardware.contains("CUDA"))
            config = "cuda";
        else if (hardware.contains("CPU"))
            config = "cpu";

        if (!"auto".equals(config)) {
            String envForHardware = org.example.MediManage.service.ai.PythonEnvironmentManager.mapBackendToEnv(config);
            String currentEnv = envManager.getActiveEnvironment();
            if (!envForHardware.equals(currentEnv)) {
                envManager.setActiveEnvironment(envForHardware);
                prefs.put("active_python_env", envForHardware);
                loadEnvironmentList();

                // Ask to restart server with new env
                javafx.scene.control.Alert restart = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.CONFIRMATION,
                        "Hardware changed to " + hardware + ".\nRestart AI Engine with '"
                                + envForHardware + "' environment now?",
                        javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
                restart.setTitle("Environment Switch");
                restart.setHeaderText("Restart AI Engine?");
                restart.showAndWait().ifPresent(response -> {
                    if (response == javafx.scene.control.ButtonType.YES) {
                        MediManageApplication app = MediManageApplication.getInstance();
                        if (app != null) {
                            app.restartServer();
                            // Reload model after new server is up
                            scheduleModelReload();
                        }
                    }
                });
            }
        }
    }

    private void triggerModelReload() {
        String modelPath = modelPathField.getText().trim();
        if (modelPath.isEmpty()) {
            return;
        }

        String hardware = hardwareCombo.getValue();
        String config = "auto";
        if (hardware.contains("OpenVINO"))
            config = "openvino";
        else if (hardware.contains("DirectML") || hardware.contains("Ryzen"))
            config = "directml";
        else if (hardware.contains("CUDA"))
            config = "cuda";
        else if (hardware.contains("CPU"))
            config = "cpu";

        try {
            JSONObject json = new JSONObject();
            json.put("model_path", modelPath);
            json.put("hardware_config", config);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:5000/load_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        javafx.application.Platform.runLater(() -> {
                            if (response.statusCode() == 200) {
                                showAlert(Alert.AlertType.INFORMATION, "Model Loaded",
                                        "AI Model reloaded successfully: " + response.body());
                            } else {
                                showAlert(Alert.AlertType.WARNING, "Model Load Failed",
                                        "Could not reload model: " + response.body());
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        // Silent — engine might be offline
                        return null;
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Schedule a model reload after server restart.
     * Waits for the new server to become healthy, then triggers model load.
     */
    private void scheduleModelReload() {
        new Thread(() -> {
            try {
                // Wait for the new server to start up
                for (int i = 0; i < 15; i++) {
                    Thread.sleep(1000);
                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("http://127.0.0.1:5000/health"))
                                .GET().build();
                        HttpResponse<String> resp = httpClient.send(req,
                                HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200) {
                            System.out.println("✅ New AI Engine is healthy — reloading model...");
                            javafx.application.Platform.runLater(this::triggerModelReload);
                            return;
                        }
                    } catch (Exception ignored) {
                        // Server not up yet, keep waiting
                    }
                }
                System.err.println("⚠️ Timeout waiting for AI Engine — model not auto-loaded.");
            } catch (InterruptedException ignored) {
            }
        }, "model-reload-scheduler").start();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
