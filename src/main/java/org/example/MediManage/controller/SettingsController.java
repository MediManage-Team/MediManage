package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.example.MediManage.MediManageApplication;
import org.example.MediManage.config.DatabaseConfig;
import org.example.MediManage.dao.AuditLogDAO;
import org.example.MediManage.dao.MessageTemplateDAO;
import org.example.MediManage.dao.ReceiptSettingsDAO;
import org.example.MediManage.model.AuditEvent;
import org.example.MediManage.model.MessageTemplate;
import org.example.MediManage.model.ReceiptSettings;
import org.example.MediManage.security.CloudApiKeyStore;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.DatabaseMaintenanceService;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.util.UserSession;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsController {
    private static final Logger LOGGER = Logger.getLogger(SettingsController.class.getName());

    @FXML private TextField sqlitePathField;
    @FXML private Label lblDbResolvedPath;
    @FXML private Label lblDbFileSize;
    @FXML private Label lblDbJournalMode;
    @FXML private Label lblDbIntegrity;
    @FXML private Label lblDbCounts;
    @FXML private Label lblDbOpsCounts;
    @FXML private Label lblDbLastBackup;
    @FXML private ListView<String> listBackupHistory;
    @FXML private ListView<String> listAuditEvents;

    @FXML private ComboBox<String> providerCombo;
    @FXML private ComboBox<String> modelCombo;
    @FXML private PasswordField geminiKeyField;
    @FXML private PasswordField groqKeyField;
    @FXML private PasswordField openrouterKeyField;
    @FXML private PasswordField openaiKeyField;
    @FXML private PasswordField claudeKeyField;

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
    @FXML private TextArea txtReceiptFooter;
    @FXML private CheckBox chkReceiptShowBarcode;

    @FXML private Label lblWhatsAppStatus;
    @FXML private Label lblWhatsAppHint;
    @FXML private Label lblWhatsAppQrPlaceholder;
    @FXML private ImageView imgQRCode;
    @FXML private Button btnRefreshWhatsApp;
    @FXML private Button btnStartBridge;
    @FXML private Button btnDisconnectWhatsApp;

    @FXML private TextArea txtWhatsAppTemplate;
    @FXML private TextField txtEmailSubjectTemplate;
    @FXML private TextArea txtEmailBodyTemplate;

    private final MessageTemplateDAO messageTemplateDAO = new MessageTemplateDAO();
    private final ReceiptSettingsDAO receiptSettingsDAO = new ReceiptSettingsDAO();
    private final DatabaseMaintenanceService databaseMaintenanceService = new DatabaseMaintenanceService();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Preferences prefs;

    private static final Map<CloudApiKeyStore.Provider, List<String>> CLOUD_MODELS = Map.of(
            CloudApiKeyStore.Provider.GEMINI, List.of("gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-pro"),
            CloudApiKeyStore.Provider.GROQ, List.of("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "gemma2-9b-it"),
            CloudApiKeyStore.Provider.OPENROUTER, List.of("anthropic/claude-3.5-sonnet", "google/gemini-2.5-flash", "meta-llama/llama-3.3-70b-instruct"),
            CloudApiKeyStore.Provider.OPENAI, List.of("gpt-4o", "gpt-4o-mini", "o1-mini"),
            CloudApiKeyStore.Provider.CLAUDE, List.of("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
    );

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(MediManageApplication.class);
        setupDatabaseSettings();
        setupOperationsLists();
        handleRefreshDatabaseHealth();
        handleRefreshAuditTrail();
        initializeCloudSettings();
        initializeCommunicationSettings();
        loadReceiptSettings();
        loadMessageTemplates();
        Platform.runLater(this::handleRefreshWhatsApp);
    }

    private void initializeCloudSettings() {
        for (CloudApiKeyStore.Provider provider : CloudApiKeyStore.Provider.values()) {
            providerCombo.getItems().add(provider.name());
        }
        String savedProvider = prefs.get("cloud_provider", "GEMINI");
        providerCombo.setValue(savedProvider);
        populateModels(savedProvider);
        providerCombo.setOnAction(event -> populateModels(providerCombo.getValue()));

        geminiKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.GEMINI));
        groqKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.GROQ));
        openrouterKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.OPENROUTER));
        openaiKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.OPENAI));
        claudeKeyField.setText(CloudApiKeyStore.get(CloudApiKeyStore.Provider.CLAUDE));
    }

    private void initializeCommunicationSettings() {
        smtpHostField.setText(prefs.get("smtp_host", "smtp.gmail.com"));
        smtpPortField.setText(prefs.get("smtp_port", "587"));
        smtpUserField.setText(prefs.get("smtp_user", ""));
        smtpPassField.setText(org.example.MediManage.security.SecureSecretStore.get("smtp_pass"));
    }

    private void setupDatabaseSettings() {
        DatabaseConfig.ConnectionSettings current = DatabaseConfig.getCurrentSettings();
        String sqlitePath = current.sqlitePath();
        if (sqlitePath == null || sqlitePath.isBlank()) {
            sqlitePath = new File(System.getProperty("user.dir"), "medimanage.db").getAbsolutePath();
        }
        sqlitePathField.setText(sqlitePath);
    }

    private void setupOperationsLists() {
        listBackupHistory.setPlaceholder(new Label("No backups recorded yet."));
        listAuditEvents.setPlaceholder(new Label("No audit events available."));
    }

    private void loadMessageTemplates() {
        try {
            MessageTemplate wa = messageTemplateDAO.getByKey(MessageTemplateDAO.KEY_WHATSAPP_INVOICE);
            MessageTemplate subject = messageTemplateDAO.getByKey(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT);
            MessageTemplate body = messageTemplateDAO.getByKey(MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY);
            if (wa != null) txtWhatsAppTemplate.setText(wa.getBodyTemplate());
            if (subject != null) txtEmailSubjectTemplate.setText(subject.getBodyTemplate());
            if (body != null) txtEmailBodyTemplate.setText(body.getBodyTemplate());
        } catch (Exception e) {
            LOGGER.warning("Failed to load message templates: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveTemplates() {
        try {
            messageTemplateDAO.save(new MessageTemplate(0, MessageTemplateDAO.KEY_WHATSAPP_INVOICE, null, txtWhatsAppTemplate.getText()));
            messageTemplateDAO.save(new MessageTemplate(0, MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT, txtEmailSubjectTemplate.getText(), txtEmailSubjectTemplate.getText()));
            messageTemplateDAO.save(new MessageTemplate(0, MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY, null, txtEmailBodyTemplate.getText()));
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

    private void loadReceiptSettings() {
        try {
            populateReceiptSettings(receiptSettingsDAO.getSettings());
        } catch (Exception e) {
            LOGGER.warning("Failed to load receipt settings: " + e.getMessage());
            populateReceiptSettings(new ReceiptSettings());
        }
    }

    private void populateReceiptSettings(ReceiptSettings settings) {
        ReceiptSettings value = settings == null ? new ReceiptSettings() : settings;
        txtReceiptPharmacyName.setText(nullToEmpty(value.getPharmacyName()));
        txtReceiptAddressLine1.setText(nullToEmpty(value.getAddressLine1()));
        txtReceiptAddressLine2.setText(nullToEmpty(value.getAddressLine2()));
        txtReceiptPhone.setText(nullToEmpty(value.getPhone()));
        txtReceiptEmail.setText(nullToEmpty(value.getEmail()));
        txtReceiptGstNumber.setText(nullToEmpty(value.getGstNumber()));
        txtReceiptLogoPath.setText(nullToEmpty(value.getLogoPath()));
        txtReceiptFooter.setText(nullToEmpty(value.getFooterText()));
        chkReceiptShowBarcode.setSelected(value.isShowBarcodeOnReceipt());
    }

    @FXML
    private void handleRefreshDatabaseHealth() {
        AppExecutors.runBackground(() -> {
            try {
                DatabaseMaintenanceService.DatabaseHealthSnapshot snapshot = databaseMaintenanceService.getHealthSnapshot();
                List<DatabaseMaintenanceService.BackupHistoryEntry> backups = databaseMaintenanceService.getRecentBackups(8);
                Platform.runLater(() -> {
                    lblDbResolvedPath.setText(snapshot.databasePath());
                    lblDbFileSize.setText(formatBytes(snapshot.fileSizeBytes()));
                    lblDbJournalMode.setText(snapshot.journalMode());
                    lblDbIntegrity.setText(snapshot.integrityStatus());
                    lblDbCounts.setText(snapshot.usersCount() + " / " + snapshot.medicinesCount() + " / " + snapshot.billsCount());
                    lblDbOpsCounts.setText(snapshot.activeBatchCount() + " / " + snapshot.auditEventCount());
                    lblDbLastBackup.setText(snapshot.lastBackupAt() == null || snapshot.lastBackupAt().isBlank() ? "No backup recorded" : snapshot.lastBackupAt());
                    listBackupHistory.setItems(javafx.collections.FXCollections.observableArrayList(
                            backups.stream()
                                    .map(entry -> entry.createdAt() + " | " + entry.backupType() + " | " + formatBytes(entry.fileSizeBytes()) + " | " + entry.backupPath())
                                    .toList()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Database Health Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleCreateBackup() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Database Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Backup", "*.db"));
        chooser.setInitialFileName("medimanage_backup_" + java.time.LocalDate.now() + ".db");
        File file = chooser.showSaveDialog(sqlitePathField.getScene().getWindow());
        if (file == null) return;
        AppExecutors.runBackground(() -> {
            try {
                databaseMaintenanceService.createBackup(file, currentUserId(), "Manual backup from Settings");
                Platform.runLater(() -> {
                    handleRefreshDatabaseHealth();
                    logSettingsEvent("DATABASE_BACKUP_CREATED", "Created database backup", new JSONObject().put("backupPath", file.getAbsolutePath()));
                    showAlert(Alert.AlertType.INFORMATION, "Backup Created", "Backup saved to " + file.getAbsolutePath());
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Backup Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleRestoreBackup() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore Backup");
        confirm.setHeaderText("Restore a database backup?");
        confirm.setContentText("This replaces the active database file. A safety backup of the current database is created first.");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Backup", "*.db", "*.sqlite"));
        File file = chooser.showOpenDialog(sqlitePathField.getScene().getWindow());
        if (file == null) return;

        AppExecutors.runBackground(() -> {
            try {
                databaseMaintenanceService.restoreBackup(file, currentUserId(), "Restore initiated from Settings");
                Platform.runLater(() -> {
                    handleRefreshDatabaseHealth();
                    handleRefreshAuditTrail();
                    logSettingsEvent("DATABASE_RESTORED", "Restored database backup", new JSONObject().put("sourcePath", file.getAbsolutePath()));
                    showAlert(Alert.AlertType.INFORMATION, "Restore Complete", "Backup restored successfully. Restart the application if open screens show stale data.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Restore Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleRefreshAuditTrail() {
        AppExecutors.runBackground(() -> {
            try {
                List<AuditEvent> events = auditLogDAO.getRecentEvents(25);
                Platform.runLater(() -> listAuditEvents.setItems(javafx.collections.FXCollections.observableArrayList(
                        events.stream()
                                .map(event -> event.occurredAt() + " | " + event.eventType() + " | " + event.summary()
                                        + (event.actorUsername() == null || event.actorUsername().isBlank() ? "" : " | by " + event.actorUsername()))
                                .toList())));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Audit Load Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleBrowseDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select SQLite Database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database Files", "*.db", "*.sqlite"));
        String currentPath = prefs.get(DatabaseConfig.PREF_DB_PATH, "medimanage.db");
        if (!currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                chooser.setInitialDirectory(currentFile.getParentFile());
            }
        }
        File selected = chooser.showOpenDialog(sqlitePathField.getScene().getWindow());
        if (selected != null) sqlitePathField.setText(selected.getAbsolutePath());
    }

    @FXML
    private void handleTestDatabaseConnection() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        try {
            DatabaseConfig.ConnectionSettings settings = buildDatabaseSettingsFromForm();
            DatabaseConfig.testConnection(settings);
            showAlert(Alert.AlertType.INFORMATION, "Database Test Successful", "Connection established using " + settings.backend().name() + ".");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Database Test Failed", "Could not connect with current settings:\n" + e.getMessage());
        }
    }

    @FXML
    private void handleClearDemoData() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "This will permanently wipe the active database and restore only the default login:\n\n- Username: admin\n- Password: admin\n\nAll operational data will be deleted.",
                javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.CANCEL);
        confirm.setTitle("Clear All Operational Data");
        confirm.setHeaderText("Irreversible Database Reset");
        applySharedDialogStyles(confirm.getDialogPane());
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.YES) return;

        javafx.scene.control.TextInputDialog typedConfirm = new javafx.scene.control.TextInputDialog();
        typedConfirm.setTitle("Final Confirmation");
        typedConfirm.setHeaderText("Type RESET DEMO DATA to continue");
        typedConfirm.setContentText("Confirmation phrase:");
        applySharedDialogStyles(typedConfirm.getDialogPane());
        String phrase = typedConfirm.showAndWait().orElse("");
        if (!"RESET DEMO DATA".equals(phrase == null ? "" : phrase.trim())) {
            showAlert(Alert.AlertType.WARNING, "Reset Cancelled", "The confirmation phrase did not match. No data was deleted.");
            return;
        }

        AppExecutors.runBackground(() -> {
            try {
                DatabaseUtil.clearDemoData();
                Platform.runLater(() -> {
                    handleRefreshDatabaseHealth();
                    handleRefreshAuditTrail();
                    showAlert(Alert.AlertType.INFORMATION, "System Reset Successful", "The database has been cleared and reset to the default admin/admin login.");
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to clear demo data.", e);
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "System Reset Failed", "Failed to wipe demo data:\n" + e.getMessage()));
            }
        });
    }

    @FXML
    private void handleSaveDatabase() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        try {
            DatabaseConfig.ConnectionSettings settings = buildDatabaseSettingsFromForm();
            DatabaseConfig.testConnection(settings);
            saveDatabaseSettings(settings);
            DatabaseUtil.initDB();
            prefs.flush();
            showAlert(Alert.AlertType.INFORMATION, "Database Saved", "Database configuration saved successfully.\nRestart the application if open screens show stale data.");
            org.example.MediManage.util.ToastNotification.success("Database configuration saved.");
            logSettingsEvent("DATABASE_SETTINGS_SAVED", "Saved database settings", new JSONObject().put("sqlitePath", settings.sqlitePath() == null ? "" : settings.sqlitePath()));
            handleRefreshDatabaseHealth();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Database Settings Error", "Could not save database settings:\n" + e.getMessage());
            org.example.MediManage.util.ToastNotification.error("DB settings error: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveCloudAI() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        if (providerCombo.getValue() != null) prefs.put("cloud_provider", providerCombo.getValue());
        if (modelCombo.getValue() != null) prefs.put("cloud_model", modelCombo.getValue());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.GEMINI, geminiKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.GROQ, groqKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.OPENROUTER, openrouterKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.OPENAI, openaiKeyField.getText());
        CloudApiKeyStore.put(CloudApiKeyStore.Provider.CLAUDE, claudeKeyField.getText());
        try { prefs.flush(); } catch (Exception ignored) {}
        org.example.MediManage.util.ToastNotification.success("Cloud AI settings saved.");
        logSettingsEvent("CLOUD_AI_SETTINGS_SAVED", "Saved cloud AI settings",
                new JSONObject()
                        .put("provider", providerCombo.getValue() == null ? "" : providerCombo.getValue())
                        .put("model", modelCombo.getValue() == null ? "" : modelCombo.getValue()));
    }

    @FXML
    private void handleSaveCommunication() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        prefs.put("smtp_host", smtpHostField.getText().trim());
        prefs.put("smtp_port", smtpPortField.getText().trim());
        prefs.put("smtp_user", smtpUserField.getText().trim());
        org.example.MediManage.security.SecureSecretStore.put("smtp_pass", smtpPassField.getText());
        try { prefs.flush(); } catch (Exception ignored) {}
        org.example.MediManage.util.ToastNotification.success("Communication settings saved.");
        logSettingsEvent("COMMUNICATION_SETTINGS_SAVED", "Saved SMTP communication settings",
                new JSONObject()
                        .put("smtpHost", smtpHostField.getText().trim())
                        .put("smtpPort", smtpPortField.getText().trim())
                        .put("smtpUser", smtpUserField.getText().trim()));
    }

    @FXML
    private void handleBrowseReceiptLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Receipt Logo");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        String currentPath = txtReceiptLogoPath.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                chooser.setInitialDirectory(currentFile.getParentFile());
            }
        }
        File selected = chooser.showOpenDialog(txtReceiptLogoPath.getScene().getWindow());
        if (selected != null) txtReceiptLogoPath.setText(selected.getAbsolutePath());
    }

    @FXML
    private void handleSaveReceiptSettings() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        ReceiptSettings settings = new ReceiptSettings();
        settings.setPharmacyName(valueOrDefault(txtReceiptPharmacyName, "MediManage Pharmacy"));
        settings.setAddressLine1(valueOrDefault(txtReceiptAddressLine1, ""));
        settings.setAddressLine2(valueOrDefault(txtReceiptAddressLine2, ""));
        settings.setPhone(valueOrDefault(txtReceiptPhone, ""));
        settings.setEmail(valueOrDefault(txtReceiptEmail, ""));
        settings.setGstNumber(valueOrDefault(txtReceiptGstNumber, ""));
        settings.setLogoPath(valueOrDefault(txtReceiptLogoPath, ""));
        settings.setFooterText(valueOrDefault(txtReceiptFooter, "Thank you for your purchase!"));
        settings.setShowBarcodeOnReceipt(chkReceiptShowBarcode.isSelected());
        try {
            receiptSettingsDAO.saveSettings(settings);
            org.example.MediManage.util.ToastNotification.success("Receipt branding saved.");
            logSettingsEvent("RECEIPT_BRANDING_SAVED", "Saved receipt branding",
                    new JSONObject()
                            .put("pharmacyName", settings.getPharmacyName())
                            .put("phone", settings.getPhone())
                            .put("email", settings.getEmail())
                            .put("showBarcode", settings.isShowBarcodeOnReceipt()));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Receipt Settings Error", "Failed to save receipt branding: " + e.getMessage());
        }
    }

    private DatabaseConfig.ConnectionSettings buildDatabaseSettingsFromForm() {
        return new DatabaseConfig.ConnectionSettings(DatabaseConfig.Backend.SQLITE, sqlitePathField.getText().trim());
    }

    private void saveDatabaseSettings(DatabaseConfig.ConnectionSettings settings) {
        prefs.put(DatabaseConfig.PREF_DB_BACKEND, settings.backend().name().toLowerCase());
        prefs.put(DatabaseConfig.PREF_DB_PATH, settings.sqlitePath() == null ? "" : settings.sqlitePath());
        System.setProperty(DatabaseConfig.DB_BACKEND_PROPERTY, settings.backend().name().toLowerCase());
        System.setProperty(DatabaseConfig.DB_PATH_PROPERTY, settings.sqlitePath() == null ? "" : settings.sqlitePath());
    }

    private void populateModels(String providerName) {
        modelCombo.getItems().clear();
        if (providerName == null || providerName.isBlank()) return;
        try {
            CloudApiKeyStore.Provider provider = CloudApiKeyStore.Provider.valueOf(providerName);
            String savedModel = prefs.get("cloud_model", "");
            boolean foundSaved = false;
            for (String model : CLOUD_MODELS.getOrDefault(provider, List.of())) {
                modelCombo.getItems().add(model);
                if (model.equals(savedModel)) {
                    modelCombo.setValue(model);
                    foundSaved = true;
                }
            }
            if (!foundSaved && !modelCombo.getItems().isEmpty()) modelCombo.setValue(modelCombo.getItems().get(0));
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void handleRefreshWhatsApp() {
        applyWhatsAppState("CHECKING");
        setWhatsAppQrImage(null);
        setWhatsAppQrPlaceholder("Checking WhatsApp Bridge status...");
        AppExecutors.runBackground(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.statusUrl()))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String status = new JSONObject(response.body()).optString("status", "UNKNOWN");
                Platform.runLater(() -> {
                    applyWhatsAppState(status);
                    if ("QR_REQUIRED".equalsIgnoreCase(status)) {
                        fetchAndDisplayQR();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> applyWhatsAppState("NOT_RUNNING"));
            }
        });
    }

    private void fetchAndDisplayQR() {
        AppExecutors.runBackground(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.qrUrl()))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject json = new JSONObject(response.body());
                if (!json.has("qr") || json.isNull("qr")) return;
                String qrData = json.getString("qr");
                com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
                com.google.zxing.common.BitMatrix matrix = writer.encode(qrData, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200);
                javafx.scene.image.WritableImage image = new javafx.scene.image.WritableImage(200, 200);
                javafx.scene.image.PixelWriter pixelWriter = image.getPixelWriter();
                for (int x = 0; x < 200; x++) {
                    for (int y = 0; y < 200; y++) {
                        pixelWriter.setColor(x, y, matrix.get(x, y) ? javafx.scene.paint.Color.BLACK : javafx.scene.paint.Color.WHITE);
                    }
                }
                Platform.runLater(() -> {
                    setWhatsAppQrImage(image);
                    lblWhatsAppHint.setText("Open WhatsApp on the phone, then scan this QR from Linked Devices.");
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to render WhatsApp QR.", e);
            }
        });
    }

    @FXML
    private void handleStartWhatsAppBridge() {
        applyWhatsAppState("INITIALIZING");
        AppExecutors.runBackground(() -> {
            try {
                MediManageApplication app = MediManageApplication.getInstance();
                if (app == null) throw new IllegalStateException("Application instance is unavailable.");
                app.startWhatsAppBridge();
                Thread.sleep(3000);
                Platform.runLater(this::handleRefreshWhatsApp);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to start WhatsApp bridge.", e);
                Platform.runLater(() -> {
                    applyWhatsAppState("NOT_RUNNING");
                    lblWhatsAppHint.setText("Bridge failed to start: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleDisconnectWhatsApp() {
        applyWhatsAppState("DISCONNECTING");
        AppExecutors.runBackground(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.logoutUrl()))
                        .POST(HttpRequest.BodyPublishers.noBody());
                org.example.MediManage.config.WhatsAppBridgeConfig.applyAdminHeader(builder);
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        applyWhatsAppState("DISCONNECTED");
                        showAlert(Alert.AlertType.INFORMATION, "WhatsApp", "WhatsApp disconnected successfully. Scan QR to reconnect.");
                    } else {
                        applyWhatsAppState("CONNECTED");
                        lblWhatsAppHint.setText("Disconnect failed. Try again.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> applyWhatsAppState("NOT_RUNNING"));
            }
        });
    }

    private void applyWhatsAppState(String rawStatus) {
        String status = rawStatus == null ? "UNKNOWN" : rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
        switch (status) {
            case "CONNECTED" -> {
                lblWhatsAppStatus.setText("Connected");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #5fe6b3; -fx-font-weight: bold;");
                lblWhatsAppHint.setText("WhatsApp is linked and ready to send invoices.");
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("WhatsApp is already linked.");
                btnStartBridge.setText("Connected");
                btnStartBridge.setDisable(true);
                btnRefreshWhatsApp.setDisable(false);
                setWhatsAppDisconnectVisible(true);
            }
            case "QR_REQUIRED", "DISCONNECTED" -> {
                lblWhatsAppStatus.setText("Scan QR to connect");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #e8c66a; -fx-font-weight: bold;");
                lblWhatsAppHint.setText("Open WhatsApp on the phone, then scan the QR from Linked Devices.");
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("Waiting for a fresh QR code...");
                btnStartBridge.setText("Refresh QR");
                btnStartBridge.setDisable(false);
                btnRefreshWhatsApp.setDisable(false);
                setWhatsAppDisconnectVisible(true);
            }
            case "INITIALIZING", "CHECKING", "DISCONNECTING", "AUTHENTICATING" -> {
                lblWhatsAppStatus.setText(status.equals("DISCONNECTING") ? "Disconnecting..." : "Starting...");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #e8c66a; -fx-font-weight: bold;");
                lblWhatsAppHint.setText("Please wait while the WhatsApp bridge updates.");
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("QR code will appear here if pairing is needed.");
                btnStartBridge.setText("Working...");
                btnStartBridge.setDisable(true);
                btnRefreshWhatsApp.setDisable(true);
                setWhatsAppDisconnectVisible(false);
            }
            default -> {
                lblWhatsAppStatus.setText("Not connected");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
                lblWhatsAppHint.setText("Tap Start WhatsApp. A QR code appears here only when pairing is required.");
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("QR code appears here after WhatsApp starts.");
                btnStartBridge.setText("Start WhatsApp");
                btnStartBridge.setDisable(false);
                btnRefreshWhatsApp.setDisable(false);
                setWhatsAppDisconnectVisible(false);
            }
        }
    }

    private void setWhatsAppQrImage(Image image) {
        imgQRCode.setImage(image);
        imgQRCode.setVisible(image != null);
        lblWhatsAppQrPlaceholder.setVisible(image == null);
    }

    private void setWhatsAppQrPlaceholder(String text) {
        lblWhatsAppQrPlaceholder.setText(text);
    }

    private void setWhatsAppDisconnectVisible(boolean visible) {
        btnDisconnectWhatsApp.setVisible(visible);
        btnDisconnectWhatsApp.setManaged(visible);
    }

    private Integer currentUserId() {
        return UserSession.getInstance().getUser() == null ? null : UserSession.getInstance().getUser().getId();
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

    private String valueOrDefault(javafx.scene.control.TextInputControl field, String defaultValue) {
        if (field == null || field.getText() == null || field.getText().trim().isEmpty()) return defaultValue;
        return field.getText().trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb);
        return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private void logSettingsEvent(String eventType, String summary, JSONObject details) {
        try {
            auditLogDAO.logEvent(currentUserId(), eventType, "SETTINGS", null, summary, details == null ? "" : details.toString());
            handleRefreshAuditTrail();
        } catch (Exception ignored) {
        }
    }

    private void applySharedDialogStyles(javafx.scene.control.DialogPane dialogPane) {
        if (dialogPane == null) return;
        java.net.URL css = getClass().getResource("/org/example/MediManage/css/common.css");
        if (css != null && !dialogPane.getStylesheets().contains(css.toExternalForm())) {
            dialogPane.getStylesheets().add(css.toExternalForm());
        }
        if (!dialogPane.getStyleClass().contains("alert-danger")) {
            dialogPane.getStyleClass().add("alert-danger");
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
