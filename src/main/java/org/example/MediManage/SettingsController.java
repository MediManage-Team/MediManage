package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
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

import org.example.MediManage.config.DatabaseConfig;
import org.example.MediManage.config.FeatureFlag;
import org.example.MediManage.config.FeatureFlags;
import org.example.MediManage.security.CloudApiKeyStore;
import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.DatabaseMigrationService;
import org.example.MediManage.service.ai.CloudAIService;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.util.UserSession;
import org.example.MediManage.util.AppExecutors;

public class SettingsController {
    private static final String DB_BACKEND_SQLITE = "SQLite (Default)";
    private static final String DB_BACKEND_POSTGRES = "PostgreSQL";

    // --- Database ---
    @FXML
    private ComboBox<String> dbBackendCombo;
    @FXML
    private TextField sqlitePathField;
    @FXML
    private javafx.scene.layout.VBox postgresConfigBox;
    @FXML
    private TextField postgresHostField;
    @FXML
    private TextField postgresPortField;
    @FXML
    private TextField postgresDatabaseField;
    @FXML
    private TextField postgresUserField;
    @FXML
    private PasswordField postgresPasswordField;
    @FXML
    private Button migrateSqliteToPostgresButton;
    @FXML
    private Button saveSettingsButton;

    // --- Local AI ---
    @FXML
    private TextField modelPathField;
    @FXML
    private ComboBox<String> hardwareCombo;
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

    // --- Environments ---
    @FXML
    private javafx.scene.layout.VBox envContainer;
    @FXML
    private javafx.scene.control.TextArea logConsole;

    private final org.example.MediManage.service.ai.PythonEnvironmentManager envManager = AIServiceProvider
            .get().getEnvManager();

    private Preferences prefs;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final DatabaseMigrationService migrationService = new DatabaseMigrationService();

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(SettingsController.class);

        // --- Database ---
        setupDatabaseSettings();

        // --- Local AI ---
        modelPathField.setText(prefs.get("local_model_path", ""));

        hardwareCombo.getItems().addAll("Auto", "CUDA (NVIDIA)", "DirectML (AMD)", "CPU Only");
        hardwareCombo.setValue(prefs.get("ai_hardware", "Auto"));

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

        // Environment list
        setupLogStreaming();
        loadEnvironmentList();
        applyPhaseZeroGovernanceGuards();
    }

    private void setupDatabaseSettings() {
        if (dbBackendCombo == null) {
            return;
        }

        dbBackendCombo.getItems().setAll(DB_BACKEND_SQLITE, DB_BACKEND_POSTGRES);
        DatabaseConfig.ConnectionSettings current = DatabaseConfig.getCurrentSettings();
        dbBackendCombo.setValue(current.backend() == DatabaseConfig.Backend.POSTGRESQL
                ? DB_BACKEND_POSTGRES
                : DB_BACKEND_SQLITE);

        if (sqlitePathField != null) {
            String sqlitePath = current.sqlitePath();
            if (sqlitePath == null || sqlitePath.isBlank()) {
                sqlitePath = new java.io.File(System.getProperty("user.dir"), "medimanage.db").getAbsolutePath();
            }
            sqlitePathField.setText(sqlitePath);
        }
        if (postgresHostField != null) {
            postgresHostField.setText(current.postgresHost());
        }
        if (postgresPortField != null) {
            postgresPortField.setText(String.valueOf(current.postgresPort()));
        }
        if (postgresDatabaseField != null) {
            postgresDatabaseField.setText(current.postgresDatabase());
        }
        if (postgresUserField != null) {
            postgresUserField.setText(current.postgresUser());
        }
        if (postgresPasswordField != null) {
            postgresPasswordField.setText(current.postgresPassword());
        }

        dbBackendCombo.setOnAction(event -> updateDatabaseFieldVisibility());
        updateDatabaseFieldVisibility();
    }

    private void updateDatabaseFieldVisibility() {
        if (postgresConfigBox == null) {
            return;
        }
        boolean postgresSelected = DB_BACKEND_POSTGRES.equals(dbBackendCombo.getValue());
        postgresConfigBox.setManaged(postgresSelected);
        postgresConfigBox.setVisible(postgresSelected);
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
                java.net.URL url = new java.net.URL("https://huggingface.co/api/whoami-v2");
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

    @FXML
    private void handleMigrateSqliteToPostgres() {
        if (!FeatureFlags.isEnabled(FeatureFlag.POSTGRES_MIGRATION)) {
            showAlert(Alert.AlertType.WARNING, "Migration Disabled",
                    "SQLite to PostgreSQL migration is currently disabled by feature flag.");
            return;
        }
        if (!enforcePermission(Permission.EXECUTE_DATABASE_MIGRATION)) {
            return;
        }
        try {
            if (!DB_BACKEND_POSTGRES.equals(dbBackendCombo.getValue())) {
                showAlert(Alert.AlertType.WARNING, "Migration Not Available",
                        "Select PostgreSQL as the target backend before starting migration.");
                return;
            }

            DatabaseConfig.ConnectionSettings postgresSettings = buildDatabaseSettingsFromForm();
            DatabaseConfig.ConnectionSettings sqliteSettings = buildSqliteSourceSettings();

            DatabaseConfig.testConnection(sqliteSettings);
            DatabaseConfig.testConnection(postgresSettings);

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Migration");
            confirm.setHeaderText("Migrate SQLite data to PostgreSQL?");
            confirm.setContentText("This will replace all existing PostgreSQL table data with SQLite data.\n"
                    + "A SQLite backup file will be created before migration.");
            confirm.showAndWait().ifPresent(response -> {
                if (response == javafx.scene.control.ButtonType.YES || response == javafx.scene.control.ButtonType.OK) {
                    runMigrationAsync(sqliteSettings, postgresSettings);
                }
            });
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Migration Setup Error", e.getMessage());
        }
    }

    private void runMigrationAsync(
            DatabaseConfig.ConnectionSettings sqliteSettings,
            DatabaseConfig.ConnectionSettings postgresSettings) {
        AppExecutors.runBackground(() -> {
            try {
                DatabaseMigrationService.MigrationResult result = migrationService
                        .migrateSqliteToPostgres(sqliteSettings, postgresSettings);
                javafx.application.Platform.runLater(() -> {
                    StringBuilder summary = new StringBuilder();
                    summary.append("SQLite backup: ").append(result.backupPath()).append("\n\n");
                    summary.append("Rows migrated:\n");
                    result.migratedRowsByTable().forEach((table, rows) -> summary
                            .append(" - ").append(table).append(": ").append(rows).append('\n'));
                    summary.append(
                            "\nMigration complete. Click Save Settings to persist PostgreSQL as active backend.");
                    showAlert(Alert.AlertType.INFORMATION, "Migration Successful", summary.toString());
                });
            } catch (Exception migrationError) {
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Migration Failed",
                        migrationError.getMessage()));
            }
        });
    }

    private DatabaseConfig.ConnectionSettings buildDatabaseSettingsFromForm() {
        DatabaseConfig.Backend backend = DB_BACKEND_POSTGRES.equals(dbBackendCombo.getValue())
                ? DatabaseConfig.Backend.POSTGRESQL
                : DatabaseConfig.Backend.SQLITE;

        String sqlitePath = sqlitePathField == null ? "" : sqlitePathField.getText().trim();
        String pgHost = postgresHostField == null ? "" : postgresHostField.getText().trim();
        String pgPortRaw = postgresPortField == null ? "" : postgresPortField.getText().trim();
        String pgDatabase = postgresDatabaseField == null ? "" : postgresDatabaseField.getText().trim();
        String pgUser = postgresUserField == null ? "" : postgresUserField.getText().trim();
        String pgPassword = postgresPasswordField == null ? "" : postgresPasswordField.getText();

        int pgPort = 5432;
        if (!pgPortRaw.isBlank()) {
            try {
                pgPort = Integer.parseInt(pgPortRaw);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("PostgreSQL port must be a valid number.");
            }
        }
        if (pgPort <= 0 || pgPort > 65535) {
            throw new IllegalArgumentException("PostgreSQL port must be between 1 and 65535.");
        }

        return new DatabaseConfig.ConnectionSettings(
                backend,
                sqlitePath,
                pgHost,
                pgPort,
                pgDatabase,
                pgUser,
                pgPassword);
    }

    private DatabaseConfig.ConnectionSettings buildSqliteSourceSettings() {
        String sqlitePath = sqlitePathField == null ? "" : sqlitePathField.getText().trim();
        return new DatabaseConfig.ConnectionSettings(
                DatabaseConfig.Backend.SQLITE,
                sqlitePath,
                "",
                5432,
                "",
                "",
                "");
    }

    private void saveDatabaseSettings(DatabaseConfig.ConnectionSettings settings) {
        prefs.put(DatabaseConfig.PREF_DB_BACKEND, settings.backend().name().toLowerCase());
        prefs.put(DatabaseConfig.PREF_DB_PATH, settings.sqlitePath() == null ? "" : settings.sqlitePath());
        prefs.put(DatabaseConfig.PREF_PG_HOST, settings.postgresHost() == null ? "" : settings.postgresHost());
        prefs.put(DatabaseConfig.PREF_PG_PORT, String.valueOf(settings.postgresPort()));
        prefs.put(DatabaseConfig.PREF_PG_DATABASE,
                settings.postgresDatabase() == null ? "" : settings.postgresDatabase());
        prefs.put(DatabaseConfig.PREF_PG_USER, settings.postgresUser() == null ? "" : settings.postgresUser());
        prefs.put(DatabaseConfig.PREF_PG_PASSWORD,
                settings.postgresPassword() == null ? "" : settings.postgresPassword());

        System.setProperty(DatabaseConfig.DB_BACKEND_PROPERTY, settings.backend().name().toLowerCase());
        System.setProperty(DatabaseConfig.DB_PATH_PROPERTY, settings.sqlitePath() == null ? "" : settings.sqlitePath());
        System.setProperty(DatabaseConfig.DB_PG_HOST_PROPERTY,
                settings.postgresHost() == null ? "" : settings.postgresHost());
        System.setProperty(DatabaseConfig.DB_PG_PORT_PROPERTY, String.valueOf(settings.postgresPort()));
        System.setProperty(DatabaseConfig.DB_PG_DATABASE_PROPERTY,
                settings.postgresDatabase() == null ? "" : settings.postgresDatabase());
        System.setProperty(DatabaseConfig.DB_PG_USER_PROPERTY,
                settings.postgresUser() == null ? "" : settings.postgresUser());
        System.setProperty(DatabaseConfig.DB_PG_PASSWORD_PROPERTY,
                settings.postgresPassword() == null ? "" : settings.postgresPassword());
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

        // Local AI
        prefs.put("local_model_path", modelPathField.getText());
        prefs.put("ai_hardware", hardwareCombo.getValue());
        prefs.put("hf_token", hfTokenField.getText().trim());

        // Cloud AI — Provider & Model
        String provider = providerCombo.getValue();
        String modelId = getSelectedModelId();
        if (provider != null)
            prefs.put("cloud_provider", provider);
        if (modelId != null)
            prefs.put("cloud_model", modelId);

        // Cloud AI — API Keys (secure store)
        CloudApiKeyStore.put(CloudAIService.Provider.GEMINI, geminiKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.GROQ, groqKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.OPENROUTER, openrouterKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.OPENAI, openaiKeyField.getText());
        CloudApiKeyStore.put(CloudAIService.Provider.CLAUDE, claudeKeyField.getText());

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
                "Configuration saved.\nDatabase: " + dbSettings.backend().name()
                        + "\nCloud AI: " + provider + " / " + (modelId != null ? modelId : "default")
                        + "\nIf database backend changed, restart the app to reconnect cleanly.");
        org.example.MediManage.util.ToastNotification.success("Settings Saved");

        // Trigger local model reload if path set
        triggerModelReload();

        // Sync the active Python env to match the hardware selection
        String hardware = hardwareCombo.getValue();
        String config = "auto";
        if (hardware.contains("CUDA"))
            config = "cuda";
        else if (hardware.contains("DirectML"))
            config = "directml";
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

    private boolean enforcePermission(Permission permission) {
        try {
            RbacPolicy.requireCurrentUser(permission);
            return true;
        } catch (SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Access Denied", e.getMessage());
            return false;
        }
    }

    private boolean canCurrentUser(Permission permission) {
        org.example.MediManage.model.User currentUser = UserSession.getInstance().getUser();
        return currentUser != null && RbacPolicy.canAccess(currentUser.getRole(), permission);
    }

    private void applyPhaseZeroGovernanceGuards() {
        boolean canManageSettings = canCurrentUser(Permission.MANAGE_SYSTEM_SETTINGS);
        if (saveSettingsButton != null) {
            saveSettingsButton.setDisable(!canManageSettings);
        }

        boolean migrationFlagEnabled = FeatureFlags.isEnabled(FeatureFlag.POSTGRES_MIGRATION);
        boolean canMigrate = migrationFlagEnabled && canCurrentUser(Permission.EXECUTE_DATABASE_MIGRATION);
        if (migrateSqliteToPostgresButton != null) {
            migrateSqliteToPostgresButton.setManaged(migrationFlagEnabled);
            migrateSqliteToPostgresButton.setVisible(migrationFlagEnabled);
            migrateSqliteToPostgresButton.setDisable(!canMigrate);
        }
    }

    private void triggerModelReload() {
        String modelPath = modelPathField.getText().trim();
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
