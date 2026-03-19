package org.example.MediManage.controller;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

import org.example.MediManage.config.DatabaseConfig;
import org.example.MediManage.dao.AuditLogDAO;
import org.example.MediManage.dao.ReceiptSettingsDAO;
import org.example.MediManage.model.AuditEvent;
import org.example.MediManage.model.ReceiptSettings;
import org.example.MediManage.security.CloudApiKeyStore;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.DatabaseMaintenanceService;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.MediManageApplication;

public class SettingsController {
    private static final Logger LOGGER = Logger.getLogger(SettingsController.class.getName());
    // --- Database ---
    @FXML
    private TextField sqlitePathField;
    @FXML
    private javafx.scene.control.Label lblDbResolvedPath;
    @FXML
    private javafx.scene.control.Label lblDbFileSize;
    @FXML
    private javafx.scene.control.Label lblDbJournalMode;
    @FXML
    private javafx.scene.control.Label lblDbIntegrity;
    @FXML
    private javafx.scene.control.Label lblDbCounts;
    @FXML
    private javafx.scene.control.Label lblDbOpsCounts;
    @FXML
    private javafx.scene.control.Label lblDbLastBackup;
    @FXML
    private javafx.scene.control.ListView<String> listBackupHistory;
    @FXML
    private javafx.scene.control.ListView<String> listAuditEvents;

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
    @FXML
    private javafx.scene.control.Label localModelStatusLabel;

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

    // --- Communication ---
    @FXML private TextField smtpHostField;
    @FXML private TextField smtpPortField;
    @FXML private TextField smtpUserField;
    @FXML private PasswordField smtpPassField;
    @FXML private TextField txtReceiptPharmacyName;
    @FXML private TextField txtReceiptAddressLine1;
    @FXML private TextField txtReceiptAddressLine2;
    @FXML private TextField txtReceiptPhone;
    @FXML private TextField txtReceiptEmail;
    @FXML private TextField txtReceiptGstNumber;
    @FXML private TextField txtReceiptLogoPath;
    @FXML private javafx.scene.control.TextArea txtReceiptFooter;
    @FXML private javafx.scene.control.CheckBox chkReceiptShowBarcode;
    
    // --- Local WhatsApp Bridge ---
    @FXML private javafx.scene.control.Label lblWhatsAppStatus;
    @FXML private javafx.scene.control.Label lblWhatsAppHint;
    @FXML private javafx.scene.control.Label lblWhatsAppQrPlaceholder;
    @FXML private javafx.scene.image.ImageView imgQRCode;
    @FXML private javafx.scene.control.Button btnRefreshWhatsApp;
    @FXML private javafx.scene.control.Button btnStartBridge;
    @FXML private javafx.scene.control.Button btnDisconnectWhatsApp;

    // --- Customer Communication Templates ---
    @FXML private javafx.scene.control.TextArea txtWhatsAppTemplate;
    @FXML private TextField txtEmailSubjectTemplate;
    @FXML private javafx.scene.control.TextArea txtEmailBodyTemplate;
    private final org.example.MediManage.dao.MessageTemplateDAO messageTemplateDAO = new org.example.MediManage.dao.MessageTemplateDAO();
    private final ReceiptSettingsDAO receiptSettingsDAO = new ReceiptSettingsDAO();
    private final DatabaseMaintenanceService databaseMaintenanceService = new DatabaseMaintenanceService();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private final org.example.MediManage.service.ai.PythonEnvironmentManager envManager = AIServiceProvider
            .get().getEnvManager();

    private Preferences prefs;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private volatile String currentWhatsAppState = "UNKNOWN";
    private java.util.concurrent.ScheduledFuture<?> whatsAppRefreshPoller;

    private static final java.util.Map<CloudApiKeyStore.Provider, java.util.List<String>> CLOUD_MODELS = java.util.Map.of(
        CloudApiKeyStore.Provider.GEMINI, java.util.List.of("gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-pro"),
        CloudApiKeyStore.Provider.GROQ, java.util.List.of("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "gemma2-9b-it"),
        CloudApiKeyStore.Provider.OPENROUTER, java.util.List.of("anthropic/claude-3.5-sonnet", "google/gemini-2.5-flash", "meta-llama/llama-3.3-70b-instruct"),
        CloudApiKeyStore.Provider.OPENAI, java.util.List.of("gpt-4o", "gpt-4o-mini", "o1-mini"),
        CloudApiKeyStore.Provider.CLAUDE, java.util.List.of("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
    );

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);

        // --- Database ---
        setupDatabaseSettings();
        setupOperationsLists();
        handleRefreshDatabaseHealth();
        handleRefreshAuditTrail();

        // --- Local AI ---
        setupLocalModelsCombo();
        if (localModelCombo != null) {
            localModelCombo.getSelectionModel().selectedItemProperty()
                    .addListener((obs, oldVal, newVal) -> updateLocalModelStatus());
        }

        hardwareCombo.getItems().addAll("Auto", "Cloud Only (Base)", "CUDA (NVIDIA)", "CPU Only");
        hardwareCombo.setValue(prefs.get("ai_hardware", "Auto"));
        updateLocalModelStatus();
        
        loadEnvironmentList();

        // Migrate legacy plaintext hf_token to encrypted SecureSecretStore
        String hfTokenVal = org.example.MediManage.security.SecureSecretStore.get("hf_token");
        if ((hfTokenVal == null || hfTokenVal.isBlank())) {
            String legacyHf = prefs.get("hf_token", "");
            if (legacyHf != null && !legacyHf.isBlank()) {
                org.example.MediManage.security.SecureSecretStore.put("hf_token", legacyHf);
                prefs.remove("hf_token");
                hfTokenVal = legacyHf;
            }
        }
        hfTokenField.setText(hfTokenVal != null ? hfTokenVal : "");

        // --- Cloud AI ---
        // Provider ComboBox
        for (CloudApiKeyStore.Provider p : CloudApiKeyStore.Provider.values()) {
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
        geminiKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.GEMINI));
        groqKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.GROQ));
        openrouterKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.OPENROUTER));
        openaiKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.OPENAI));
        claudeKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.CLAUDE));

        // --- Communication ---
        smtpHostField.setText(prefs.get("smtp_host", "smtp.gmail.com"));
        smtpPortField.setText(prefs.get("smtp_port", "587"));
        smtpUserField.setText(prefs.get("smtp_user", ""));
        smtpPassField.setText(org.example.MediManage.security.SecureSecretStore.get("smtp_pass"));
        loadReceiptSettings();

        // Initial load of WhatsApp Status
        javafx.application.Platform.runLater(this::handleRefreshWhatsApp);

        // --- Message Templates ---
        loadMessageTemplates();
    }

    private void loadMessageTemplates() {
        try {
            var wa = messageTemplateDAO.getByKey(org.example.MediManage.dao.MessageTemplateDAO.KEY_WHATSAPP_INVOICE);
            if (wa != null && txtWhatsAppTemplate != null) txtWhatsAppTemplate.setText(wa.getBodyTemplate());

            var emailSubject = messageTemplateDAO.getByKey(org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT);
            if (emailSubject != null && txtEmailSubjectTemplate != null) txtEmailSubjectTemplate.setText(emailSubject.getBodyTemplate());

            var emailBody = messageTemplateDAO.getByKey(org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY);
            if (emailBody != null && txtEmailBodyTemplate != null) txtEmailBodyTemplate.setText(emailBody.getBodyTemplate());
        } catch (Exception e) {
            LOGGER.warning("Failed to load message templates: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveTemplates() {
        try {
            if (txtWhatsAppTemplate != null) {
                var t = new org.example.MediManage.model.MessageTemplate(0, org.example.MediManage.dao.MessageTemplateDAO.KEY_WHATSAPP_INVOICE, null, txtWhatsAppTemplate.getText());
                messageTemplateDAO.save(t);
            }
            if (txtEmailSubjectTemplate != null) {
                var t = new org.example.MediManage.model.MessageTemplate(0, org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT, txtEmailSubjectTemplate.getText(), txtEmailSubjectTemplate.getText());
                messageTemplateDAO.save(t);
            }
            if (txtEmailBodyTemplate != null) {
                var t = new org.example.MediManage.model.MessageTemplate(0, org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY, null, txtEmailBodyTemplate.getText());
                messageTemplateDAO.save(t);
            }
            showAlert(Alert.AlertType.INFORMATION, "Templates Saved", "Message templates updated successfully.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to save templates: " + e.getMessage());
        }
    }

    @FXML
    private void handleResetTemplates() {
        try {
            messageTemplateDAO.resetAllToDefaults();
            loadMessageTemplates();
            showAlert(Alert.AlertType.INFORMATION, "Templates Reset", "All templates restored to defaults.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to reset templates: " + e.getMessage());
        }
    }

    private void setupLocalModelsCombo() {
        String savedPath = prefs.get("local_model_path", "");

        AppExecutors.runBackground(() -> {
            try {
                org.example.MediManage.service.ai.AIOrchestrator orchestrator = new org.example.MediManage.service.ai.AIOrchestrator();
                org.json.JSONArray installedModels = orchestrator.listLocalModels();
                java.util.Map<String, LocalModelItem> uniqueModels = new java.util.LinkedHashMap<>();

                if (installedModels != null) {
                    for (int i = 0; i < installedModels.length(); i++) {
                        org.json.JSONObject model = installedModels.getJSONObject(i);
                        String name = model.optString("name", "Unknown");
                        String path = model.optString("path", "");
                        if (!path.isBlank()) {
                            uniqueModels.putIfAbsent(path.toLowerCase(java.util.Locale.ROOT), new LocalModelItem(name, path));
                        }
                    }
                }
                
                javafx.application.Platform.runLater(() -> {
                    boolean foundSaved = false;

                    localModelCombo.getItems().clear();

                    java.util.List<LocalModelItem> sortedItems = new java.util.ArrayList<>(uniqueModels.values());
                    sortedItems.sort(java.util.Comparator.comparing(item -> item.name.toLowerCase(java.util.Locale.ROOT)));

                    for (LocalModelItem item : sortedItems) {
                        localModelCombo.getItems().add(item);
                        if (item.path.equals(savedPath)) {
                            localModelCombo.setValue(item);
                            foundSaved = true;
                        }
                    }

                    // If a custom path was saved that isn't in the models dir, add it manually
                    if (!foundSaved && !savedPath.isEmpty()) {
                        File f = new File(savedPath);
                        if (f.exists()) {
                            LocalModelItem customItem = new LocalModelItem(f.getName(), savedPath);
                            localModelCombo.getItems().add(customItem);
                            localModelCombo.setValue(customItem);
                        } else {
                            prefs.remove("local_model_path");
                        }
                    }

                    updateLocalModelStatus();
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load local model list.", e);
                javafx.application.Platform.runLater(() -> {
                    if (localModelCombo != null) {
                        localModelCombo.getItems().clear();
                    }
                    updateLocalModelStatus();
                });
            }
        });
    }

    @FXML
    private void handleRefreshLocalModels() {
        setupLocalModelsCombo();
        org.example.MediManage.util.ToastNotification.info("Refreshing installed models...");
    }

    @FXML
    private void handleLoadSelectedModel() {
        loadSelectedModel(true, true);
    }

    private void updateLocalModelStatus() {
        if (localModelStatusLabel == null) {
            return;
        }

        LocalModelItem selected = localModelCombo != null ? localModelCombo.getValue() : null;
        String selectedName = selected != null ? selected.name : "No model selected";
        String selectedPath = selected != null ? selected.path : prefs.get("local_model_path", "");

        AppExecutors.runBackground(() -> {
            try {
                org.json.JSONObject health = AIServiceProvider.get().getLocalService().getHealth();
                boolean loaded = health.optBoolean("model_loaded", false);
                String modelName = health.optString("model_name", "").trim();
                String provider = health.optString("provider", "").trim();
                String statusText;

                if (loaded) {
                    statusText = "Loaded now: " + (modelName.isBlank() ? selectedName : modelName)
                            + (provider.isBlank() ? "" : " on " + provider);
                } else if (!selectedPath.isBlank()) {
                    statusText = "Selected: " + selectedName + " • not loaded yet";
                } else {
                    statusText = "Choose a model, then click Load Selected.";
                }

                final String text = statusText;
                javafx.application.Platform.runLater(() -> localModelStatusLabel.setText(text));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        localModelStatusLabel.setText(selectedPath.isBlank()
                                ? "Choose a model, then click Load Selected."
                                : "Selected: " + selectedName + " • engine status unavailable"));
            }
        });
    }

    private void loadSelectedModel(boolean persistChoice, boolean showFeedback) {
        LocalModelItem selectedModel = localModelCombo != null ? localModelCombo.getValue() : null;
        if (selectedModel == null || selectedModel.path == null || selectedModel.path.isBlank()) {
            if (showFeedback) {
                showAlert(Alert.AlertType.WARNING, "No Model Selected", "Select a local model first.");
            }
            return;
        }

        if (localModelStatusLabel != null) {
            localModelStatusLabel.setText("Loading " + selectedModel.name + "...");
        }

        AppExecutors.runBackground(() -> {
            org.example.MediManage.service.ai.LocalAIService.ModelLoadResult result = AIServiceProvider.get()
                    .getLocalService()
                    .loadModelBlocking(selectedModel.path, resolveHardwareConfig());

            javafx.application.Platform.runLater(() -> {
                if (result.success()) {
                    if (persistChoice) {
                        prefs.put("local_model_path", result.modelPath());
                        try { prefs.flush(); } catch (Exception ignored) {}
                    }
                    updateLocalModelStatus();
                    if (showFeedback) {
                        org.example.MediManage.util.ToastNotification.success("Loaded " + selectedModel.name);
                    }
                } else {
                    String message = "Could not load model: " + result.message();
                    localModelStatusLabel.setText(message);
                    if (showFeedback) {
                        showAlert(Alert.AlertType.WARNING, "Model Load Failed", message);
                    }
                }
            });
        });
    }

    private String resolveHardwareConfig() {
        String hardware = hardwareCombo != null ? hardwareCombo.getValue() : prefs.get("ai_hardware", "Auto");
        if (hardware == null) {
            return "auto";
        }
        if (hardware.contains("CUDA")) {
            return "cuda";
        }
        if (hardware.contains("CPU")) {
            return "cpu";
        }
        if (hardware.contains("Base")) {
            return "auto";
        }
        return "auto";
    }

    private void syncHardwareSelectionForEnv(String envName) {
        if (hardwareCombo == null || envName == null) {
            return;
        }
        switch (envName.toLowerCase(java.util.Locale.ROOT)) {
            case "gpu" -> hardwareCombo.setValue("CUDA (NVIDIA)");
            case "cpu" -> hardwareCombo.setValue("CPU Only");
            case "base" -> hardwareCombo.setValue("Cloud Only (Base)");
            default -> {
            }
        }
    }

    private void loadReceiptSettings() {
        try {
            populateReceiptSettings(receiptSettingsDAO.getSettings());
        } catch (Exception e) {
            LOGGER.warning("Failed to load receipt settings: " + e.getMessage());
            populateReceiptSettings(new ReceiptSettings());
        }
    }

    private void populateReceiptSettings(ReceiptSettings settings) {
        if (settings == null) {
            settings = new ReceiptSettings();
        }
        if (txtReceiptPharmacyName != null) txtReceiptPharmacyName.setText(nullToEmpty(settings.getPharmacyName()));
        if (txtReceiptAddressLine1 != null) txtReceiptAddressLine1.setText(nullToEmpty(settings.getAddressLine1()));
        if (txtReceiptAddressLine2 != null) txtReceiptAddressLine2.setText(nullToEmpty(settings.getAddressLine2()));
        if (txtReceiptPhone != null) txtReceiptPhone.setText(nullToEmpty(settings.getPhone()));
        if (txtReceiptEmail != null) txtReceiptEmail.setText(nullToEmpty(settings.getEmail()));
        if (txtReceiptGstNumber != null) txtReceiptGstNumber.setText(nullToEmpty(settings.getGstNumber()));
        if (txtReceiptLogoPath != null) txtReceiptLogoPath.setText(nullToEmpty(settings.getLogoPath()));
        if (txtReceiptFooter != null) txtReceiptFooter.setText(nullToEmpty(settings.getFooterText()));
        if (chkReceiptShowBarcode != null) chkReceiptShowBarcode.setSelected(settings.isShowBarcodeOnReceipt());
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

    private void setupOperationsLists() {
        if (listBackupHistory != null) {
            listBackupHistory.setPlaceholder(new javafx.scene.control.Label("No backups recorded yet."));
        }
        if (listAuditEvents != null) {
            listAuditEvents.setPlaceholder(new javafx.scene.control.Label("No audit events available."));
        }
    }

    @FXML
    private void handleRefreshDatabaseHealth() {
        AppExecutors.runBackground(() -> {
            try {
                DatabaseMaintenanceService.DatabaseHealthSnapshot snapshot = databaseMaintenanceService.getHealthSnapshot();
                java.util.List<DatabaseMaintenanceService.BackupHistoryEntry> backups = databaseMaintenanceService.getRecentBackups(8);
                javafx.application.Platform.runLater(() -> {
                    if (lblDbResolvedPath != null) lblDbResolvedPath.setText(snapshot.databasePath());
                    if (lblDbFileSize != null) lblDbFileSize.setText(formatBytes(snapshot.fileSizeBytes()));
                    if (lblDbJournalMode != null) lblDbJournalMode.setText(snapshot.journalMode());
                    if (lblDbIntegrity != null) lblDbIntegrity.setText(snapshot.integrityStatus());
                    if (lblDbCounts != null) {
                        lblDbCounts.setText(snapshot.usersCount() + " / " + snapshot.medicinesCount() + " / " + snapshot.billsCount());
                    }
                    if (lblDbOpsCounts != null) {
                        lblDbOpsCounts.setText(snapshot.activeBatchCount() + " / " + snapshot.auditEventCount());
                    }
                    if (lblDbLastBackup != null) {
                        lblDbLastBackup.setText(snapshot.lastBackupAt() == null || snapshot.lastBackupAt().isBlank()
                                ? "No backup recorded"
                                : snapshot.lastBackupAt());
                    }
                    if (listBackupHistory != null) {
                        listBackupHistory.setItems(javafx.collections.FXCollections.observableArrayList(
                                backups.stream()
                                        .map(entry -> entry.createdAt() + " | " + entry.backupType()
                                                + " | " + formatBytes(entry.fileSizeBytes())
                                                + " | " + entry.backupPath())
                                        .toList()));
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Database Health Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleCreateBackup() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Database Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Backup", "*.db"));
        chooser.setInitialFileName("medimanage_backup_" + java.time.LocalDate.now() + ".db");
        File file = chooser.showSaveDialog(sqlitePathField == null ? null : sqlitePathField.getScene().getWindow());
        if (file == null) {
            return;
        }
        Integer actorUserId = org.example.MediManage.util.UserSession.getInstance().getUser() == null
                ? null
                : org.example.MediManage.util.UserSession.getInstance().getUser().getId();
        AppExecutors.runBackground(() -> {
            try {
                databaseMaintenanceService.createBackup(file, actorUserId, "Manual backup from Settings");
                javafx.application.Platform.runLater(() -> {
                    handleRefreshDatabaseHealth();
                    logSettingsEvent("DATABASE_BACKUP_CREATED", "Created database backup", new JSONObject()
                            .put("backupPath", file.getAbsolutePath()));
                    showAlert(Alert.AlertType.INFORMATION, "Backup Created", "Backup saved to " + file.getAbsolutePath());
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Backup Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleRestoreBackup() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore Backup");
        confirm.setHeaderText("Restore a database backup?");
        confirm.setContentText("This will replace the active database file. A safety backup of the current DB will be created first.");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Backup", "*.db", "*.sqlite"));
        File file = chooser.showOpenDialog(sqlitePathField == null ? null : sqlitePathField.getScene().getWindow());
        if (file == null) {
            return;
        }
        Integer actorUserId = org.example.MediManage.util.UserSession.getInstance().getUser() == null
                ? null
                : org.example.MediManage.util.UserSession.getInstance().getUser().getId();
        AppExecutors.runBackground(() -> {
            try {
                databaseMaintenanceService.restoreBackup(file, actorUserId, "Restore initiated from Settings");
                javafx.application.Platform.runLater(() -> {
                    handleRefreshDatabaseHealth();
                    handleRefreshAuditTrail();
                    logSettingsEvent("DATABASE_RESTORED", "Restored database backup", new JSONObject()
                            .put("sourcePath", file.getAbsolutePath()));
                    showAlert(Alert.AlertType.INFORMATION, "Restore Complete",
                            "Backup restored successfully. Restart the application if any open screens show stale data.");
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Restore Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleRefreshAuditTrail() {
        AppExecutors.runBackground(() -> {
            try {
                java.util.List<AuditEvent> events = auditLogDAO.getRecentEvents(25);
                javafx.application.Platform.runLater(() -> {
                    if (listAuditEvents != null) {
                        listAuditEvents.setItems(javafx.collections.FXCollections.observableArrayList(
                                events.stream()
                                        .map(event -> event.occurredAt()
                                                + " | " + event.eventType()
                                                + " | " + event.summary()
                                                + (event.actorUsername() == null || event.actorUsername().isBlank()
                                                        ? ""
                                                        : " | by " + event.actorUsername()))
                                        .toList()));
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Audit Load Failed", e.getMessage()));
            }
        });
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
    private void handleClearDemoData() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
            "⚠️ WARNING: PERMANENT DATA DELETION ⚠️\n\n" +
            "This action will permanently delete ALL operational data in the database, including:\n" +
            "- Customers & Loyalty Points\n" +
            "- Medicines & Stock Inventory\n" +
            "- Historical Bills & Sales Data\n" +
            "- Orders, Suppliers, and Expenses\n\n" +
            "Only configuration (Active Users, Passwords, Message Templates) will remain.\n" +
            "Are you absolutely sure you want to completely wipe the system?",
            javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
        confirm.setTitle("Clear All Operational Data");
        confirm.setHeaderText("Irreversible Action");
        
        // Add styling for severe warning
        javafx.scene.control.DialogPane dialogPane = confirm.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        dialogPane.getStyleClass().add("alert-danger");

        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.YES) {
                try {
                    DatabaseUtil.clearDemoData();
                    showAlert(Alert.AlertType.INFORMATION, "System Reset Successful", 
                        "All operational data has been deleted.\nThe system is now clean and ready for real usage.\n\nPlease restart the application.");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to clear operational data.", e);
                    showAlert(Alert.AlertType.ERROR, "System Reset Failed", 
                        "Failed to wipe demo data:\n" + e.getMessage());
                }
            }
        });
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
            CloudApiKeyStore.Provider provider = CloudApiKeyStore.Provider.valueOf(providerName);
            String savedModel = prefs.get("cloud_model", "");
            boolean foundSaved = false;

            for (String model : CLOUD_MODELS.getOrDefault(provider, java.util.Collections.emptyList())) {
                modelCombo.getItems().add(model);
                if (model.equals(savedModel)) {
                    modelCombo.setValue(model);
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
        return modelCombo.getValue();
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
                LOGGER.log(Level.WARNING, "Failed to load environment list.", e);
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
                LOGGER.log(Level.WARNING, "Failed to install or update environment " + envName + ".", e);
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
        syncHardwareSelectionForEnv(envName);
        if (hardwareCombo != null && hardwareCombo.getValue() != null) {
            prefs.put("ai_hardware", hardwareCombo.getValue());
        }
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
                    if (shouldAutoReloadLocalModel()) {
                        scheduleModelReload();
                    }
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
        updateLocalModelStatus();
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
            stage.setOnHidden(event -> {
                setupLocalModelsCombo();
                updateLocalModelStatus();
            });
            stage.show();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not open model store.", e);
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
        logSettingsEvent("DATABASE_SETTINGS_SAVED", "Saved database settings", new JSONObject()
                .put("sqlitePath", dbSettings.sqlitePath() == null ? "" : dbSettings.sqlitePath()));
        handleRefreshDatabaseHealth();
    }

    @FXML
    private void handleSaveLocalAI() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }

        String hardware = hardwareCombo.getValue();
        prefs.put("ai_hardware", hardware);

        String hfToken = hfTokenField.getText().trim();
        org.example.MediManage.security.SecureSecretStore.put("hf_token", hfToken);
        prefs.remove("hf_token"); // Clean up legacy plaintext if present

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
                LOGGER.log(Level.WARNING, "Failed to provision local AI environment.", e);
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
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.GEMINI, geminiKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.GROQ, groqKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.OPENROUTER, openrouterKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.OPENAI, openaiKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.CLAUDE, claudeKeyField.getText());

        try { prefs.flush(); } catch (Exception ignored) {}

        org.example.MediManage.util.ToastNotification.success("Cloud AI Settings Saved.\nRequests will now route through Python Orchestrator using these credentials.");
    }

    @FXML
    private void handleSaveCommunication() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }

        prefs.put("smtp_host", smtpHostField.getText().trim());
        prefs.put("smtp_port", smtpPortField.getText().trim());
        prefs.put("smtp_user", smtpUserField.getText().trim());
        org.example.MediManage.security.SecureSecretStore.put("smtp_pass", smtpPassField.getText());



        try { prefs.flush(); } catch (Exception ignored) {}

        org.example.MediManage.util.ToastNotification.success("Communication Settings Saved");
        logSettingsEvent("COMMUNICATION_SETTINGS_SAVED", "Saved SMTP communication settings", new JSONObject()
                .put("smtpHost", smtpHostField.getText() == null ? "" : smtpHostField.getText().trim())
                .put("smtpPort", smtpPortField.getText() == null ? "" : smtpPortField.getText().trim())
                .put("smtpUser", smtpUserField.getText() == null ? "" : smtpUserField.getText().trim()));
    }

    @FXML
    private void handleBrowseReceiptLogo() {
        if (txtReceiptLogoPath == null || txtReceiptLogoPath.getScene() == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Receipt Logo");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        String currentPath = txtReceiptLogoPath.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            File parent = currentFile.getParentFile();
            if (parent != null && parent.exists()) {
                chooser.setInitialDirectory(parent);
            }
        }

        File selected = chooser.showOpenDialog(txtReceiptLogoPath.getScene().getWindow());
        if (selected != null) {
            txtReceiptLogoPath.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void handleSaveReceiptSettings() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) {
            return;
        }

        ReceiptSettings settings = new ReceiptSettings();
        settings.setPharmacyName(valueOrDefault(txtReceiptPharmacyName, "MediManage Pharmacy"));
        settings.setAddressLine1(valueOrDefault(txtReceiptAddressLine1, ""));
        settings.setAddressLine2(valueOrDefault(txtReceiptAddressLine2, ""));
        settings.setPhone(valueOrDefault(txtReceiptPhone, ""));
        settings.setEmail(valueOrDefault(txtReceiptEmail, ""));
        settings.setGstNumber(valueOrDefault(txtReceiptGstNumber, ""));
        settings.setLogoPath(valueOrDefault(txtReceiptLogoPath, ""));
        settings.setFooterText(valueOrDefault(txtReceiptFooter, "Thank you for your purchase!"));
        settings.setShowBarcodeOnReceipt(chkReceiptShowBarcode != null && chkReceiptShowBarcode.isSelected());

        try {
            receiptSettingsDAO.saveSettings(settings);
            org.example.MediManage.util.ToastNotification.success("Receipt Branding Saved");
            logSettingsEvent("RECEIPT_BRANDING_SAVED", "Saved receipt branding", new JSONObject()
                    .put("pharmacyName", settings.getPharmacyName())
                    .put("phone", settings.getPhone())
                    .put("email", settings.getEmail())
                    .put("showBarcode", settings.isShowBarcodeOnReceipt()));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Receipt Settings Error",
                    "Failed to save receipt branding: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefreshWhatsApp() {
        lblWhatsAppStatus.setText("Checking...");
        setWhatsAppQrImage(null);
        setWhatsAppQrPlaceholder("Checking WhatsApp Bridge status...");
        
        AppExecutors.runBackground(() -> {
            try {
                // Check Status 
                HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.statusUrl()))
                    .GET()
                    .build();
                HttpResponse<String> statusResp = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
                
                JSONObject statusJson = new JSONObject(statusResp.body());
                String statusMsg = statusJson.optString("status", "UNKNOWN");
                
                javafx.application.Platform.runLater(() -> {
                    applyWhatsAppState(statusMsg);
                    if ("QR_REQUIRED".equalsIgnoreCase(statusMsg)) {
                        fetchAndDisplayQR();
                    }
                });
                
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    applyWhatsAppState("NOT_RUNNING");
                });
            }
        });
    }

    private void fetchAndDisplayQR() {
        AppExecutors.runBackground(() -> {
            try {
                HttpRequest qrRequest = HttpRequest.newBuilder()
                    .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.qrUrl()))
                    .GET()
                    .build();
                HttpResponse<String> qrResp = httpClient.send(qrRequest, HttpResponse.BodyHandlers.ofString());
                JSONObject json = new JSONObject(qrResp.body());
                if (json.has("qr") && !json.isNull("qr")) {
                    String qrData = json.getString("qr");
                    
                    // Generate a real image from the QR string text using ZXing
                    try {
                        com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
                        com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(qrData, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200);
                        
                        javafx.scene.image.WritableImage image = new javafx.scene.image.WritableImage(200, 200);
                        javafx.scene.image.PixelWriter pixelWriter = image.getPixelWriter();
                        
                        for (int x = 0; x < 200; x++) {
                            for (int y = 0; y < 200; y++) {
                                pixelWriter.setColor(x, y, bitMatrix.get(x, y) ? javafx.scene.paint.Color.BLACK : javafx.scene.paint.Color.WHITE);
                            }
                        }
                        
                        javafx.application.Platform.runLater(() -> {
                            setWhatsAppQrImage(image);
                            if (lblWhatsAppHint != null) {
                                lblWhatsAppHint.setText("Open WhatsApp on the phone, then scan this QR from Linked Devices.");
                            }
                            org.example.MediManage.util.ToastNotification.info("Scan the QR Code to Link WhatsApp Bridge!");
                        });
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Failed to render WhatsApp QR code.", ex);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to fetch WhatsApp QR code.", e);
            }
        });
    }

    @FXML
    private void handleStartWhatsAppBridge() {
        applyWhatsAppState("INITIALIZING");
        startWhatsAppAutoRefresh();

        AppExecutors.runBackground(() -> {
            try {
                org.example.MediManage.MediManageApplication app = org.example.MediManage.MediManageApplication.getInstance();
                if (app == null) {
                    throw new IllegalStateException("Application instance is unavailable.");
                }
                app.startWhatsAppBridge();

                // Wait a few seconds for the server to start, then refresh status
                Thread.sleep(3000);

                javafx.application.Platform.runLater(() -> {
                    handleRefreshWhatsApp();
                });

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to start WhatsApp bridge from Settings.", e);
                javafx.application.Platform.runLater(() -> {
                    applyWhatsAppState("NOT_RUNNING");
                    if (lblWhatsAppHint != null) {
                        lblWhatsAppHint.setText("Bridge failed to start: " + e.getMessage());
                    }
                });
            }
        });
    }

    @FXML
    private void handleDisconnectWhatsApp() {
        lblWhatsAppStatus.setText("Disconnecting...");
        lblWhatsAppStatus.setStyle("-fx-text-fill: #e8c66a; -fx-font-weight: bold;");
        if (lblWhatsAppHint != null) {
            lblWhatsAppHint.setText("Disconnecting WhatsApp. A fresh QR may be needed after this.");
        }

        AppExecutors.runBackground(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.logoutUrl()))
                    .POST(HttpRequest.BodyPublishers.noBody());
                org.example.MediManage.config.WhatsAppBridgeConfig.applyAdminHeader(requestBuilder);
                HttpRequest request = requestBuilder.build();
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                javafx.application.Platform.runLater(() -> {
                    if (resp.statusCode() == 200) {
                        applyWhatsAppState("DISCONNECTED");
                        showAlert(Alert.AlertType.INFORMATION, "WhatsApp", "WhatsApp disconnected successfully. Scan QR to reconnect.");
                        // Auto-refresh to show new QR after a delay
                        AppExecutors.schedule(() -> javafx.application.Platform.runLater(this::handleRefreshWhatsApp), 4, java.util.concurrent.TimeUnit.SECONDS);
                    } else {
                        if (lblWhatsAppHint != null) {
                            lblWhatsAppHint.setText("Disconnect failed. Try again.");
                        }
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    applyWhatsAppState("NOT_RUNNING");
                });
            }
        });
    }

    @FXML
    private void handleStopWhatsAppBridge() {
        applyWhatsAppState("INITIALIZING");

        AppExecutors.runBackground(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.shutdownUrl()))
                    .POST(HttpRequest.BodyPublishers.noBody());
                org.example.MediManage.config.WhatsAppBridgeConfig.applyAdminHeader(requestBuilder);
                HttpRequest request = requestBuilder.build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}

            javafx.application.Platform.runLater(() -> {
                applyWhatsAppState("STOPPED");
            });
        });
    }

    private void applyWhatsAppState(String rawStatus) {
        String status = rawStatus == null ? "UNKNOWN" : rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
        currentWhatsAppState = status;
        if (lblWhatsAppStatus == null) {
            return;
        }

        switch (status) {
            case "CONNECTED" -> {
                lblWhatsAppStatus.setText("Connected");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #5fe6b3; -fx-font-weight: bold;");
                if (lblWhatsAppHint != null) {
                    lblWhatsAppHint.setText("WhatsApp is linked and ready to send invoices.");
                }
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("WhatsApp is already linked.");
                if (btnStartBridge != null) {
                    btnStartBridge.setText("Connected");
                    btnStartBridge.setDisable(true);
                }
                if (btnRefreshWhatsApp != null) {
                    btnRefreshWhatsApp.setDisable(false);
                }
                setWhatsAppDisconnectVisible(true);
            }
            case "QR_REQUIRED", "DISCONNECTED" -> {
                lblWhatsAppStatus.setText("Scan QR to connect");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #e8c66a; -fx-font-weight: bold;");
                if (lblWhatsAppHint != null) {
                    lblWhatsAppHint.setText("Open WhatsApp on the phone, then scan the QR from Linked Devices.");
                }
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("Waiting for a fresh QR code...");
                if (btnStartBridge != null) {
                    btnStartBridge.setText("Refresh QR");
                    btnStartBridge.setDisable(false);
                }
                if (btnRefreshWhatsApp != null) {
                    btnRefreshWhatsApp.setDisable(false);
                }
                setWhatsAppDisconnectVisible(true);
            }
            case "INITIALIZING", "AUTHENTICATING" -> {
                lblWhatsAppStatus.setText("Starting...");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #e8c66a; -fx-font-weight: bold;");
                if (lblWhatsAppHint != null) {
                    lblWhatsAppHint.setText("The bridge is starting. This usually takes a few seconds.");
                }
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("QR code will appear here if pairing is needed.");
                if (btnStartBridge != null) {
                    btnStartBridge.setText("Starting...");
                    btnStartBridge.setDisable(true);
                }
                if (btnRefreshWhatsApp != null) {
                    btnRefreshWhatsApp.setDisable(true);
                }
                setWhatsAppDisconnectVisible(false);
            }
            default -> {
                lblWhatsAppStatus.setText("Not connected");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
                if (lblWhatsAppHint != null) {
                    lblWhatsAppHint.setText("Tap Start WhatsApp. A QR code appears here only when pairing is required.");
                }
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("QR code appears here after WhatsApp starts.");
                if (btnStartBridge != null) {
                    btnStartBridge.setText("Start WhatsApp");
                    btnStartBridge.setDisable(false);
                }
                if (btnRefreshWhatsApp != null) {
                    btnRefreshWhatsApp.setDisable(false);
                }
                setWhatsAppDisconnectVisible(false);
            }
        }

        if (!isWhatsAppPendingState()) {
            cancelWhatsAppAutoRefresh();
        }
    }

    private void setWhatsAppQrImage(javafx.scene.image.Image image) {
        if (imgQRCode != null) {
            imgQRCode.setImage(image);
            imgQRCode.setVisible(image != null);
        }
        if (lblWhatsAppQrPlaceholder != null) {
            lblWhatsAppQrPlaceholder.setVisible(image == null);
        }
    }

    private void setWhatsAppQrPlaceholder(String text) {
        if (lblWhatsAppQrPlaceholder != null) {
            lblWhatsAppQrPlaceholder.setText(text);
        }
    }

    private void setWhatsAppDisconnectVisible(boolean visible) {
        if (btnDisconnectWhatsApp != null) {
            btnDisconnectWhatsApp.setVisible(visible);
            btnDisconnectWhatsApp.setManaged(visible);
        }
    }

    private void startWhatsAppAutoRefresh() {
        cancelWhatsAppAutoRefresh();
        final java.util.concurrent.atomic.AtomicInteger pollsRemaining = new java.util.concurrent.atomic.AtomicInteger(12);
        whatsAppRefreshPoller = AppExecutors.scheduleAtFixedRate(() -> javafx.application.Platform.runLater(() -> {
            handleRefreshWhatsApp();
            if (pollsRemaining.decrementAndGet() <= 0 || !isWhatsAppPendingState()) {
                cancelWhatsAppAutoRefresh();
            }
        }), 2, 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void cancelWhatsAppAutoRefresh() {
        if (whatsAppRefreshPoller != null) {
            whatsAppRefreshPoller.cancel(false);
            whatsAppRefreshPoller = null;
        }
    }

    private boolean isWhatsAppPendingState() {
        return "INITIALIZING".equals(currentWhatsAppState) || "AUTHENTICATING".equals(currentWhatsAppState);
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
        if (!shouldAutoReloadLocalModel()) {
            return;
        }

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
        final String finalConfig = config;

        try {
            AppExecutors.runBackground(() -> {
                org.example.MediManage.service.ai.LocalAIService.ModelLoadResult result = AIServiceProvider.get()
                        .getLocalService()
                        .loadModelBlocking(modelPath, finalConfig);
                javafx.application.Platform.runLater(() -> {
                    if (result.success()) {
                        showAlert(Alert.AlertType.INFORMATION, "Model Loaded",
                                "AI model reloaded successfully.");
                    } else {
                        showAlert(Alert.AlertType.WARNING, "Model Load Failed",
                                "Could not reload model: " + result.message());
                    }
                });
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to trigger model reload.", e);
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
                            LOGGER.info("New AI Engine is healthy; reloading model.");
                            javafx.application.Platform.runLater(this::triggerModelReload);
                            return;
                        }
                    } catch (Exception ignored) {
                        // Server not up yet, keep waiting
                    }
                }
                LOGGER.warning("Timeout waiting for AI Engine; model was not auto-loaded.");
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private boolean shouldAutoReloadLocalModel() {
        String activeEnv = prefs != null
                ? prefs.get("active_python_env", envManager.getActiveEnvironment())
                : envManager.getActiveEnvironment();
        if ("base".equalsIgnoreCase(activeEnv)) {
            return false;
        }

        String hardware = hardwareCombo != null ? hardwareCombo.getValue() : null;
        return hardware == null || !hardware.toLowerCase().contains("base");
    }

    private String valueOrDefault(javafx.scene.control.TextInputControl field, String defaultValue) {
        if (field == null) {
            return defaultValue;
        }
        String value = field.getText();
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private void logSettingsEvent(String eventType, String summary, JSONObject details) {
        try {
            Integer actorUserId = org.example.MediManage.util.UserSession.getInstance().getUser() == null
                    ? null
                    : org.example.MediManage.util.UserSession.getInstance().getUser().getId();
            auditLogDAO.logEvent(actorUserId, eventType, "SETTINGS", null, summary, details == null ? "" : details.toString());
            handleRefreshAuditTrail();
        } catch (Exception ignored) {
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
