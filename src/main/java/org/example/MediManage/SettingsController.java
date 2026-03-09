package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;

import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;

import javafx.scene.layout.VBox;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.prefs.Preferences;
import org.json.JSONObject;

import org.example.MediManage.config.DatabaseConfig;
import org.example.MediManage.security.CloudApiKeyStore;
import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.ai.CloudAIService;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.util.AppExecutors;

public class SettingsController {
    // --- Database ---
    @FXML
    private TextField sqlitePathField;

    public static class LocalModelItem {
        public final String name;
        public final String path;

        public LocalModelItem(String name, String path) {
            this.name = name;
            this.path = path;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            LocalModelItem that = (LocalModelItem) obj;
            return java.util.Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(path);
        }
    }

    // --- Local AI ---
    @FXML
    private ComboBox<LocalModelItem> localModelCombo;
    @FXML
    private ComboBox<String> hardwareCombo;
    @FXML
    private VBox envContainer;
    @FXML
    private PasswordField hfTokenField;

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

    private final org.example.MediManage.service.ai.PythonEnvironmentManager envManager = AIServiceProvider
            .get().getEnvManager();

    private Preferences prefs;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(SettingsController.class);

        // --- Database ---
        setupDatabaseSettings();

        // --- Local AI ---
        setupLocalModelsCombo();

        hardwareCombo.getItems().addAll("Auto", "Cloud Only (Base)", "CUDA (NVIDIA)", "CPU Only");
        hardwareCombo.setValue(prefs.get("ai_hardware", "Auto"));
        
        loadEnvironmentList();

        hfTokenField.setText(prefs.get("hf_token", ""));

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
        geminiKeyField.setText(CloudApiKeyStore.get(CloudAIService.Provider.GEMINI));
        groqKeyField.setText(CloudApiKeyStore.get(CloudAIService.Provider.GROQ));
        openrouterKeyField.setText(CloudApiKeyStore.get(CloudAIService.Provider.OPENROUTER));
        openaiKeyField.setText(CloudApiKeyStore.get(CloudAIService.Provider.OPENAI));
        claudeKeyField.setText(CloudApiKeyStore.get(CloudAIService.Provider.CLAUDE));

    }

    private void setupLocalModelsCombo() {
        String savedPath = prefs.get("local_model_path", "");

        AppExecutors.runBackground(() -> {
            try {
                org.example.MediManage.service.ai.AIOrchestrator orchestrator = new org.example.MediManage.service.ai.AIOrchestrator();
                org.json.JSONArray installedModels = orchestrator.listLocalModels();
                
                javafx.application.Platform.runLater(() -> {
                    boolean foundSaved = false;
                    
                    if (installedModels != null) {
                        for (int i = 0; i < installedModels.length(); i++) {
                            org.json.JSONObject model = installedModels.getJSONObject(i);
                            String name = model.optString("name", "Unknown");
                            String path = model.optString("path", "");
                            LocalModelItem item = new LocalModelItem(name, path);
                            localModelCombo.getItems().add(item);
                            
                            if (path.equals(savedPath)) {
                                localModelCombo.setValue(item);
                                foundSaved = true;
                            }
                        }
                    }

                    // If a custom path was saved that isn't in the models dir, add it manually
                    if (!foundSaved && !savedPath.isEmpty()) {
                        File f = new File(savedPath);
                        LocalModelItem customItem = new LocalModelItem(f.exists() ? f.getName() : "Custom Path", savedPath);
                        localModelCombo.getItems().add(customItem);
                        localModelCombo.setValue(customItem);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupDatabaseSettings() {
        DatabaseConfig.ConnectionSettings current = DatabaseConfig.getCurrentSettings();

        if (sqlitePathField != null) {
            String sqlitePath = current.sqlitePath();
            if (sqlitePath == null || sqlitePath.isBlank()) {
                sqlitePath = new java.io.File(System.getProperty("user.dir"), "medimanage.db").getAbsolutePath();
            }
            sqlitePathField.setText(sqlitePath);
        }
    }

    @FXML
    private void handleBrowseDatabase() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select SQLite Database");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database Files (*.db, *.sqlite)", "*.db", "*.sqlite"));
        
        try {
            String currentPath = prefs.get("db_path", "medimanage.db");
            if (!currentPath.isEmpty()) {
                File currentFile = new File(currentPath);
                if (currentFile.exists() && currentFile.getParentFile() != null) {
                    fileChooser.setInitialDirectory(currentFile.getParentFile());
                } else {
                    fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
                }
            } else {
                fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
            }
        } catch (Exception ignored) {}

        File selectedFile = fileChooser.showOpenDialog(sqlitePathField.getScene().getWindow());
        if (selectedFile != null) {
            sqlitePathField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleTestDatabaseConnection() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }
        try {
            DatabaseConfig.ConnectionSettings dbSettings = buildDatabaseSettingsFromForm();
            DatabaseConfig.testConnection(dbSettings);
            showAlert(Alert.AlertType.INFORMATION, "Database Test Successful",
                    "Connection established using " + dbSettings.backend().name() + ".");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Database Test Failed",
                    "Could not connect with current settings:\n" + e.getMessage());
        }
    }

    @FXML
    private void handleTestHfToken() {
        String token = hfTokenField.getText().trim();
        if (token.isEmpty()) {
            org.example.MediManage.util.ToastNotification.warning("Enter a HuggingFace token first");
            return;
        }
        org.example.MediManage.util.ToastNotification.info("Testing HuggingFace token…");

        AppExecutors.runBackground(() -> {
            try {
                java.net.URL url = new java.net.URI("https://huggingface.co/api/whoami-v2").toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    // Parse username from response
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        sb.append(line);
                    reader.close();
                    String body = sb.toString();
                    // Simple JSON parse for "name" field
                    String username = "valid";
                    int nameIdx = body.indexOf("\"name\"");
                    if (nameIdx >= 0) {
                        int colonIdx = body.indexOf(":", nameIdx);
                        int quoteStart = body.indexOf("\"", colonIdx + 1);
                        int quoteEnd = body.indexOf("\"", quoteStart + 1);
                        if (quoteStart >= 0 && quoteEnd > quoteStart) {
                            username = body.substring(quoteStart + 1, quoteEnd);
                        }
                    }
                    final String uname = username;
                    org.example.MediManage.util.ToastNotification.success("HF Token valid — " + uname);
                } else {
                    org.example.MediManage.util.ToastNotification.error("HF Token invalid (HTTP " + code + ")");
                }
                conn.disconnect();
            } catch (Exception e) {
                org.example.MediManage.util.ToastNotification.error("HF Token test failed: " + e.getMessage());
            }
        });
    }



    private DatabaseConfig.ConnectionSettings buildDatabaseSettingsFromForm() {
        String sqlitePath = sqlitePathField == null ? "" : sqlitePathField.getText().trim();

        return new DatabaseConfig.ConnectionSettings(
                DatabaseConfig.Backend.SQLITE,
                sqlitePath);
    }

    private void saveDatabaseSettings(DatabaseConfig.ConnectionSettings settings) {
        prefs.put(DatabaseConfig.PREF_DB_BACKEND, settings.backend().name().toLowerCase());
        prefs.put(DatabaseConfig.PREF_DB_PATH, settings.sqlitePath() == null ? "" : settings.sqlitePath());

        System.setProperty(DatabaseConfig.DB_BACKEND_PROPERTY, settings.backend().name().toLowerCase());
        System.setProperty(DatabaseConfig.DB_PATH_PROPERTY, settings.sqlitePath() == null ? "" : settings.sqlitePath());
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

     // --- Environment Management ---      

    private void loadEnvironmentList() {
        if (envContainer == null) return;
        envContainer.getChildren().clear();

        AppExecutors.runBackground(() -> {
            try {
                java.util.List<java.util.Map<String, Object>> envs = envManager.getEnvironmentMetadata();
                String activeEnvName = envManager.getActiveEnvironment();

                javafx.application.Platform.runLater(() -> {
                    envContainer.getChildren().clear();
                    for (java.util.Map<String, Object> env : envs) {
                        envContainer.getChildren().add(createEnvCard(env, activeEnvName));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
        }

        AppExecutors.runBackground(() -> {
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
                    popup.appendLog("✅ Installation complete! This window will close shortly.");
                    try { Thread.sleep(2000); } catch (Exception ignored) {}
                    popup.close();
                }

                javafx.application.Platform.runLater(this::loadEnvironmentList);
            } catch (Exception e) {
                e.printStackTrace();
                if (popup != null && !popup.isClosed()) {
                    popup.appendLog("❌ Error: " + e.getMessage());
                    popup.setStatus("❌ Installation failed");
                }
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error",
                        "Failed to install environment: " + e.getMessage()));
            }
        });
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

        File selectedDir = dirChooser.showDialog(localModelCombo.getScene().getWindow());
        if (selectedDir != null) {
            addCustomLocalModelSelection(selectedDir.getAbsolutePath());
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
        File selectedFile = fileChooser.showOpenDialog(localModelCombo.getScene().getWindow());
        if (selectedFile != null) {
            addCustomLocalModelSelection(selectedFile.getAbsolutePath());
        }
    }

    private void addCustomLocalModelSelection(String path) {
        File f = new File(path);
        LocalModelItem item = new LocalModelItem(f.getName(), path);
        // Avoid duplicate
        if (!localModelCombo.getItems().contains(item)) {
            localModelCombo.getItems().add(item);
        }
        localModelCombo.setValue(item);
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

    @FXML
    private void handleViewEngineLogs() {
        StartupProgressController popup = StartupProgressController.show();
        if (popup != null) {
            popup.setStatus("📋 Streaming AI Engine Logs...");
            
            // Prime background fetching to mirror log status onto the popup safely.
            envManager.setLogCallback(msg -> {
                if (popup != null && !popup.isClosed()) {
                    popup.appendLog(msg);
                }
            });
            
            // Initial payload push since callbacks only trigger on new lines
            popup.appendLog("Monitoring Active Engine Console.\n" +
                            "Close this window to stop monitoring.\n" +
                            "---------------------------------------\n");
        }
    }

    // --- Save Settings ---

    @FXML
    private void handleSaveDatabase() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }
        DatabaseConfig.ConnectionSettings dbSettings;
        try {
            dbSettings = buildDatabaseSettingsFromForm();
            DatabaseConfig.testConnection(dbSettings);
            saveDatabaseSettings(dbSettings);
            DatabaseUtil.initDB();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Database Settings Error",
                    "Could not save database settings:\n" + e.getMessage());
            org.example.MediManage.util.ToastNotification.error("DB settings error: " + e.getMessage());
            return;
        }

        try { prefs.flush(); } catch (Exception ignored) {}

        showAlert(Alert.AlertType.INFORMATION, "Database Saved",
                "Database configuration saved successfully.\n" +
                "If the database backend changed, restart the application to reconnect cleanly.");
        org.example.MediManage.util.ToastNotification.success("Database configuration saved.");
    }

    @FXML
    private void handleSaveLocalAI() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }

        LocalModelItem selectedModel = localModelCombo.getValue();
        prefs.put("local_model_path", selectedModel != null ? selectedModel.path : "");

        String hardware = hardwareCombo.getValue();
        prefs.put("ai_hardware", hardware);

        String hfToken = hfTokenField.getText().trim();
        prefs.put("hf_token", hfToken);

        try { prefs.flush(); } catch (Exception ignored) {}

        org.example.MediManage.util.ToastNotification.success("Local AI Settings Saved");

        // Notify running server of config changes instantly
        org.example.MediManage.service.ai.LocalAIService localAI = AIServiceProvider.get().getLocalService();
        if (localAI != null) {
            localAI.updateConfig(hfToken);
        }

        // Determine environment to setup based on Hardware Acceleration logic.
        // "Auto" will determine via envManager.autoDetectBestEnv() at setup.
        String targetEnv = "cpu"; // default safe fallback
        if (hardware.contains("CUDA")) targetEnv = "gpu";
        else if (hardware.contains("DirectML")) targetEnv = "gpu"; // Legacy mapped
        else if (hardware.contains("Base") || hardware.contains("base")) targetEnv = "base";
        
        if ("Auto".equals(hardware)) {
           targetEnv = envManager.autoDetectBestEnv();
        }

        final String finalEnv = targetEnv;
        prefs.put("active_python_env", finalEnv);
        envManager.setActiveEnvironment(finalEnv);

        StartupProgressController popup = StartupProgressController.show();
        if (popup != null) {
            popup.setStatus("📦 Validating " + org.example.MediManage.service.ai.PythonEnvironmentManager.ENV_LABELS
                    .getOrDefault(finalEnv, finalEnv) + " backend...");
            popup.appendLog("⏳ Checking configuration for '" + finalEnv + "'...");
        }

        AppExecutors.runBackground(() -> {
            try {
                // Pipe installation logs to popup dialog
                envManager.setLogCallback(msg -> {
                    if (popup != null && !popup.isClosed()) {
                        popup.appendLog(msg);
                    }
                });

                // Run dependency pip installation / validation
                envManager.ensureEnvironment(finalEnv);

                if (popup != null && !popup.isClosed()) {
                    popup.setStatus("✅ AI Environment Setup Complete");
                    popup.setProgress(1.0);
                    popup.appendLog("✓ Environment validated.");
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                    popup.close();
                }
                
                // Restart server logic and trigger reloading of local models
                javafx.application.Platform.runLater(() -> {
                     loadEnvironmentList();
                     MediManageApplication app = MediManageApplication.getInstance();
                     if (app != null) {
                         app.restartServer();
                         if (!"base".equals(finalEnv)) {
                             scheduleModelReload();
                         }
                     }
                });

            } catch (Exception e) {
                e.printStackTrace();
                if (popup != null && !popup.isClosed()) {
                    popup.appendLog("❌ Error: " + e.getMessage());
                    popup.setStatus("❌ Installation failed");
                }
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Backend Update Failed",
                        "Failed to provision local environment: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void handleSaveCloudAI() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }

        String provider = providerCombo.getValue();
        String modelId = getSelectedModelId();
        if (provider != null)
            prefs.put("cloud_provider", provider);
        if (modelId != null)
            prefs.put("cloud_model", modelId);
            
        // Save secure keys
        CloudApiKeyStore.put(CloudAIService.Provider.GEMINI, geminiKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.GROQ, groqKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.OPENROUTER, openrouterKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.OPENAI, openaiKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.CLAUDE, claudeKeyField.getText());

        try { prefs.flush(); } catch (Exception ignored) {}

        // Propagate to internal orchestrator
        try {
            CloudAIService cloud = AIServiceProvider.get().getCloudService();
            CloudAIService.Provider p = CloudAIService.Provider.valueOf(provider);
            
            cloud.setApiKey(CloudAIService.Provider.GEMINI, geminiKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.GROQ, groqKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.OPENROUTER, openrouterKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.OPENAI, openaiKeyField.getText());
            cloud.setApiKey(CloudAIService.Provider.CLAUDE, claudeKeyField.getText());

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
    }

    private boolean enforcePermission(Permission permission) {
        try {
            RbacPolicy.requireCurrentUser(permission);
            return true;
        } catch (SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Access Denied", e.getMessage());
            return false;
        }
    }

    // Cleaned up duplicate
    private void triggerModelReload() {
        LocalModelItem selectedModel = localModelCombo.getValue();
        String modelPath = selectedModel != null ? selectedModel.path : "";
        if (modelPath.isEmpty()) {
            return;
        }

        String hardware = hardwareCombo.getValue();
        String config = "auto";
        if (hardware.contains("CUDA"))
            config = "cuda";
        else if (hardware.contains("CPU"))
            config = "cpu";

        try {
            JSONObject json = new JSONObject();
            json.put("model_path", modelPath);
            json.put("hardware_config", config);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:5000/load_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
            LocalAdminTokenManager.applyHeader(requestBuilder);
            HttpRequest request = requestBuilder.build();

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
        AppExecutors.runBackground(() -> {
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
                Thread.currentThread().interrupt();
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
