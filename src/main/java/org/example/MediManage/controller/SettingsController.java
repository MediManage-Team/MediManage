package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsController {
    private static final Logger LOGGER = Logger.getLogger(SettingsController.class.getName());
    private static final long WHATSAPP_STATUS_REFRESH_DELAY_SECONDS = 2L;
    private static final int MAX_WHATSAPP_AUTO_REFRESH_ATTEMPTS = 15;
    private static final String TEMPLATE_PREVIEW_CUSTOMER_NAME = "Asha Patel";
    private static final int TEMPLATE_PREVIEW_BILL_ID = 1042;
    private static final double TEMPLATE_PREVIEW_TOTAL_AMOUNT = 498.75;
    private static final String TEMPLATE_PREVIEW_CARE_NOTE =
            "Patient Care Protocol note: a personalized care guide for these medicines is included at the end of the attached PDF.";

    @FXML private TextField sqlitePathField;
    @FXML private Label lblDbResolvedPath;
    @FXML private Label lblDbFileSize;
    @FXML private Label lblDbJournalMode;
    @FXML private Label lblDbIntegrity;
    @FXML private Label lblDbCounts;
    @FXML private Label lblDbOpsCounts;
    @FXML private Label lblDbLastBackup;
    @FXML private ListView<String> listBackupHistory;
    @FXML private ListView<AuditEvent> listAuditEvents;

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
    @FXML private TextField txtInvoiceTemplatePath;
    @FXML private TextField txtReceiptTemplatePath;
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
    @FXML private TextArea txtWhatsAppPreview;
    @FXML private TextField txtEmailSubjectPreview;
    @FXML private TextArea txtEmailBodyPreview;

    private final MessageTemplateDAO messageTemplateDAO = new MessageTemplateDAO();
    private final ReceiptSettingsDAO receiptSettingsDAO = new ReceiptSettingsDAO();
    private final DatabaseMaintenanceService databaseMaintenanceService = new DatabaseMaintenanceService();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicInteger whatsAppAutoRefreshAttempts = new AtomicInteger();

    private Preferences prefs;
    private volatile ScheduledFuture<?> whatsAppStatusRefreshTask;
    private int activeReceiptSettingId;

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
        initializeTemplatePreviewBindings();
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
            sqlitePath = DatabaseConfig.getResolvedDatabaseFile().getAbsolutePath();
        }
        sqlitePathField.setText(sqlitePath);
    }

    private void setupOperationsLists() {
        listBackupHistory.setPlaceholder(new Label("No backups recorded yet."));
        listAuditEvents.setPlaceholder(new Label("No audit events available."));
        listAuditEvents.setFixedCellSize(-1);
        listAuditEvents.setCellFactory(listView -> new AuditEventListCell());
    }

    private void initializeTemplatePreviewBindings() {
        if (txtWhatsAppTemplate != null) {
            txtWhatsAppTemplate.textProperty().addListener((obs, oldValue, newValue) -> refreshTemplatePreviews());
        }
        if (txtEmailSubjectTemplate != null) {
            txtEmailSubjectTemplate.textProperty().addListener((obs, oldValue, newValue) -> refreshTemplatePreviews());
        }
        if (txtEmailBodyTemplate != null) {
            txtEmailBodyTemplate.textProperty().addListener((obs, oldValue, newValue) -> refreshTemplatePreviews());
        }
        if (txtReceiptPharmacyName != null) {
            txtReceiptPharmacyName.textProperty().addListener((obs, oldValue, newValue) -> refreshTemplatePreviews());
        }
        refreshTemplatePreviews();
    }

    private void loadMessageTemplates() {
        try {
            MessageTemplate wa = messageTemplateDAO.getByKey(MessageTemplateDAO.KEY_WHATSAPP_INVOICE);
            MessageTemplate subject = messageTemplateDAO.getByKey(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT);
            MessageTemplate body = messageTemplateDAO.getByKey(MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY);
            txtWhatsAppTemplate.setText(resolveTemplateText(wa, MessageTemplateDAO.KEY_WHATSAPP_INVOICE));
            txtEmailSubjectTemplate.setText(resolveTemplateText(subject, MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT));
            txtEmailBodyTemplate.setText(resolveTemplateText(body, MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY));
        } catch (Exception e) {
            LOGGER.warning("Failed to load message templates: " + e.getMessage());
            txtWhatsAppTemplate.setText(messageTemplateDAO.getDefaultBody(MessageTemplateDAO.KEY_WHATSAPP_INVOICE));
            txtEmailSubjectTemplate.setText(messageTemplateDAO.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT));
            txtEmailBodyTemplate.setText(messageTemplateDAO.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY));
        }
        refreshTemplatePreviews();
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
        activeReceiptSettingId = value.getSettingId();
        txtReceiptPharmacyName.setText(nullToEmpty(value.getPharmacyName()));
        txtReceiptAddressLine1.setText(nullToEmpty(value.getAddressLine1()));
        txtReceiptAddressLine2.setText(nullToEmpty(value.getAddressLine2()));
        txtReceiptPhone.setText(nullToEmpty(value.getPhone()));
        txtReceiptEmail.setText(nullToEmpty(value.getEmail()));
        txtReceiptGstNumber.setText(nullToEmpty(value.getGstNumber()));
        txtReceiptLogoPath.setText(nullToEmpty(value.getLogoPath()));
        txtReceiptFooter.setText(nullToEmpty(value.getFooterText()));
        txtInvoiceTemplatePath.setText(nullToEmpty(value.getInvoiceTemplatePath()));
        txtReceiptTemplatePath.setText(nullToEmpty(value.getReceiptTemplatePath()));
        chkReceiptShowBarcode.setSelected(value.isShowBarcodeOnReceipt());
        refreshTemplatePreviews();
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
                List<AuditEvent> events = auditLogDAO.getRecentEvents(50);
                Platform.runLater(() -> listAuditEvents.setItems(javafx.collections.FXCollections.observableArrayList(events)));
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
    private void handleBrowseInvoiceTemplate() {
        browseReportTemplate(txtInvoiceTemplatePath, "Select Invoice JRXML Template");
    }

    @FXML
    private void handleBrowseReceiptTemplate() {
        browseReportTemplate(txtReceiptTemplatePath, "Select Receipt JRXML Template");
    }

    @FXML
    private void handleClearInvoiceTemplate() {
        txtInvoiceTemplatePath.clear();
    }

    @FXML
    private void handleClearReceiptTemplate() {
        txtReceiptTemplatePath.clear();
    }

    @FXML
    private void handleSaveReceiptSettings() {
        if (!enforcePermission(Permission.MANAGE_SYSTEM_SETTINGS)) return;
        ReceiptSettings settings = new ReceiptSettings();
        settings.setSettingId(activeReceiptSettingId);
        settings.setPharmacyName(valueOrDefault(txtReceiptPharmacyName, "MediManage Pharmacy"));
        settings.setAddressLine1(valueOrDefault(txtReceiptAddressLine1, ""));
        settings.setAddressLine2(valueOrDefault(txtReceiptAddressLine2, ""));
        settings.setPhone(valueOrDefault(txtReceiptPhone, ""));
        settings.setEmail(valueOrDefault(txtReceiptEmail, ""));
        settings.setGstNumber(valueOrDefault(txtReceiptGstNumber, ""));
        settings.setLogoPath(valueOrDefault(txtReceiptLogoPath, ""));
        settings.setFooterText(valueOrDefault(txtReceiptFooter, "Thank you for your purchase!"));
        try {
            settings.setInvoiceTemplatePath(validateOptionalJrxmlPath(txtInvoiceTemplatePath, "invoice"));
            settings.setReceiptTemplatePath(validateOptionalJrxmlPath(txtReceiptTemplatePath, "receipt"));
            settings.setShowBarcodeOnReceipt(chkReceiptShowBarcode.isSelected());
            receiptSettingsDAO.saveSettings(settings);
            loadReceiptSettings();
            org.example.MediManage.util.ToastNotification.success("Receipt branding saved.");
            logSettingsEvent("RECEIPT_BRANDING_SAVED", "Saved receipt branding",
                    new JSONObject()
                            .put("pharmacyName", settings.getPharmacyName())
                            .put("phone", settings.getPhone())
                            .put("email", settings.getEmail())
                            .put("invoiceTemplate", settings.getInvoiceTemplatePath())
                            .put("receiptTemplate", settings.getReceiptTemplatePath())
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
        refreshWhatsAppStatus(true);
    }

    private void refreshWhatsAppStatus(boolean resetAutoRefreshBudget) {
        if (resetAutoRefreshBudget) {
            whatsAppAutoRefreshAttempts.set(0);
        }
        cancelWhatsAppStatusRefresh();
        applyWhatsAppState("CHECKING");
        setWhatsAppQrImage(null);
        setWhatsAppQrPlaceholder("Checking WhatsApp Bridge status...");
        AppExecutors.runBackground(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.statusUrl()))
                        .GET();
                org.example.MediManage.config.WhatsAppBridgeConfig.applyAdminHeader(requestBuilder);
                HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Bridge status request failed with HTTP " + response.statusCode());
                }
                String status = new JSONObject(response.body()).optString("status", "UNKNOWN");
                Platform.runLater(() -> updateWhatsAppStatus(status));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "WhatsApp status refresh failed.", e);
                Platform.runLater(() -> updateWhatsAppStatus("NOT_RUNNING"));
            }
        });
    }

    private void updateWhatsAppStatus(String rawStatus) {
        String status = normalizeWhatsAppStatus(rawStatus);
        applyWhatsAppState(status);
        if (shouldFetchWhatsAppQr(status)) {
            fetchAndDisplayQR();
        }
        if (isWhatsAppStatusTransient(status)) {
            scheduleWhatsAppStatusRefresh();
        } else {
            whatsAppAutoRefreshAttempts.set(0);
            cancelWhatsAppStatusRefresh();
        }
    }

    private void fetchAndDisplayQR() {
        AppExecutors.runBackground(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(org.example.MediManage.config.WhatsAppBridgeConfig.qrUrl()))
                        .GET();
                org.example.MediManage.config.WhatsAppBridgeConfig.applyAdminHeader(requestBuilder);
                HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }
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
        whatsAppAutoRefreshAttempts.set(0);
        cancelWhatsAppStatusRefresh();
        applyWhatsAppState("INITIALIZING");
        AppExecutors.runBackground(() -> {
            try {
                MediManageApplication app = MediManageApplication.getInstance();
                if (app == null) throw new IllegalStateException("Application instance is unavailable.");
                app.startWhatsAppBridge();
                scheduleWhatsAppStatusRefresh();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to start WhatsApp bridge.", e);
                Platform.runLater(() -> {
                    updateWhatsAppStatus("NOT_RUNNING");
                    lblWhatsAppHint.setText("Bridge failed to start: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleDisconnectWhatsApp() {
        cancelWhatsAppStatusRefresh();
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
                        updateWhatsAppStatus("DISCONNECTED");
                        showAlert(Alert.AlertType.INFORMATION, "WhatsApp", "WhatsApp disconnected successfully. Scan QR to reconnect.");
                    } else {
                        updateWhatsAppStatus("CONNECTED");
                        lblWhatsAppHint.setText("Disconnect failed. Try again.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> updateWhatsAppStatus("NOT_RUNNING"));
            }
        });
    }

    private void applyWhatsAppState(String rawStatus) {
        String status = normalizeWhatsAppStatus(rawStatus);
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
                lblWhatsAppStatus.setText(
                        "DISCONNECTING".equals(status)
                                ? "Disconnecting..."
                                : "CHECKING".equals(status) ? "Checking..." : "Starting...");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #e8c66a; -fx-font-weight: bold;");
                lblWhatsAppHint.setText("WhatsApp starts automatically at launch. Please wait while the bridge reconnects.");
                setWhatsAppQrImage(null);
                setWhatsAppQrPlaceholder("QR code will appear here if pairing is needed.");
                btnStartBridge.setText("Working...");
                btnStartBridge.setDisable(true);
                btnRefreshWhatsApp.setDisable(false);
                setWhatsAppDisconnectVisible(false);
            }
            default -> {
                lblWhatsAppStatus.setText("Not connected");
                lblWhatsAppStatus.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
                lblWhatsAppHint.setText("WhatsApp starts automatically at launch. If it was stopped, tap Start WhatsApp. A QR code appears only when pairing is required.");
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

    private void scheduleWhatsAppStatusRefresh() {
        if (whatsAppAutoRefreshAttempts.incrementAndGet() > MAX_WHATSAPP_AUTO_REFRESH_ATTEMPTS) {
            return;
        }
        cancelWhatsAppStatusRefresh();
        whatsAppStatusRefreshTask = AppExecutors.schedule(
                () -> Platform.runLater(() -> refreshWhatsAppStatus(false)),
                WHATSAPP_STATUS_REFRESH_DELAY_SECONDS,
                TimeUnit.SECONDS);
    }

    private void cancelWhatsAppStatusRefresh() {
        ScheduledFuture<?> task = whatsAppStatusRefreshTask;
        whatsAppStatusRefreshTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }

    static String normalizeWhatsAppStatus(String rawStatus) {
        return rawStatus == null ? "UNKNOWN" : rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
    }

    static boolean isWhatsAppStatusTransient(String rawStatus) {
        return switch (normalizeWhatsAppStatus(rawStatus)) {
            case "INITIALIZING", "CHECKING", "DISCONNECTING", "AUTHENTICATING" -> true;
            default -> false;
        };
    }

    static boolean shouldFetchWhatsAppQr(String rawStatus) {
        return "QR_REQUIRED".equals(normalizeWhatsAppStatus(rawStatus));
    }

    static String formatAuditEventTitle(AuditEvent event) {
        if (event == null) {
            return "";
        }
        String entityType = humanizeAuditToken(event.entityType());
        if (entityType.isBlank()) {
            return humanizeAuditToken(event.eventType());
        }
        return humanizeAuditToken(event.eventType()) + " on " + entityType;
    }

    static String formatAuditEventMeta(AuditEvent event) {
        if (event == null) {
            return "";
        }

        List<String> segments = new java.util.ArrayList<>();
        if (event.actorUsername() != null && !event.actorUsername().isBlank()) {
            segments.add("By " + event.actorUsername());
        } else if (event.actorUserId() != null) {
            segments.add("By user #" + event.actorUserId());
        }

        String entityToken = humanizeAuditToken(event.entityType());
        if (!entityToken.isBlank() && event.entityId() != null) {
            segments.add(entityToken + " #" + event.entityId());
        }

        String details = summarizeAuditDetails(event.detailsJson());
        if (!details.isBlank()) {
            segments.add(details);
        }

        return String.join(" | ", segments);
    }

    static String summarizeAuditDetails(String rawDetailsJson) {
        if (rawDetailsJson == null || rawDetailsJson.isBlank()) {
            return "";
        }

        try {
            JSONObject json = new JSONObject(rawDetailsJson);
            java.util.List<String> keys = json.keySet().stream()
                    .sorted()
                    .limit(4)
                    .toList();
            java.util.List<String> segments = new java.util.ArrayList<>();
            for (String key : keys) {
                Object value = json.opt(key);
                if (value == null) {
                    continue;
                }
                String renderedValue = isSensitiveAuditKey(key)
                        ? "[hidden]"
                        : shortenAuditValue(String.valueOf(value), 42);
                if (renderedValue.isBlank()) {
                    continue;
                }
                segments.add(humanizeAuditToken(key) + ": " + renderedValue);
            }
            return String.join(" | ", segments);
        } catch (Exception ignored) {
            return shortenAuditValue(rawDetailsJson.replaceAll("\\s+", " ").trim(), 140);
        }
    }

    private static boolean isSensitiveAuditKey(String key) {
        String normalized = normalizeWhatsAppStatus(key);
        return normalized.contains("PASSWORD")
                || normalized.contains("TOKEN")
                || normalized.contains("SECRET")
                || normalized.contains("KEY");
    }

    private static String shortenAuditValue(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String humanizeAuditToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        String[] parts = rawToken.trim().replace('-', '_').split("_+");
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String normalized = part.toLowerCase(java.util.Locale.ROOT);
            words.add(Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1));
        }
        return String.join(" ", words);
    }

    private static final class AuditEventListCell extends ListCell<AuditEvent> {
        private final Label titleLabel = new Label();
        private final Label timestampLabel = new Label();
        private final Label summaryLabel = new Label();
        private final Label metaLabel = new Label();
        private final Region spacer = new Region();
        private final HBox header = new HBox(10);
        private final VBox container = new VBox(4);

        private AuditEventListCell() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e6f0ff;");
            timestampLabel.getStyleClass().add("text-muted");
            summaryLabel.setWrapText(true);
            summaryLabel.setStyle("-fx-text-fill: #bfc9e6;");
            metaLabel.setWrapText(true);
            metaLabel.getStyleClass().add("text-muted");

            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(titleLabel, spacer, timestampLabel);
            container.getChildren().addAll(header, summaryLabel, metaLabel);
            container.setFillWidth(true);
            container.prefWidthProperty().bind(widthProperty().subtract(32));
        }

        @Override
        protected void updateItem(AuditEvent event, boolean empty) {
            super.updateItem(event, empty);
            if (empty || event == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            titleLabel.setText(formatAuditEventTitle(event));
            timestampLabel.setText(nullToDash(event.occurredAt()));
            summaryLabel.setText(nullToDash(event.summary()));

            String meta = formatAuditEventMeta(event);
            metaLabel.setVisible(!meta.isBlank());
            metaLabel.setManaged(!meta.isBlank());
            metaLabel.setText(meta);

            setText(null);
            setGraphic(container);
        }

        private static String nullToDash(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }
    }

    private void refreshTemplatePreviews() {
        String pharmacyName = valueOrDefault(txtReceiptPharmacyName, "MediManage Pharmacy");
        if (txtWhatsAppPreview != null) {
            txtWhatsAppPreview.setText(MessageTemplate.render(
                    valueOrDefault(txtWhatsAppTemplate, messageTemplateDAO.getDefaultBody(MessageTemplateDAO.KEY_WHATSAPP_INVOICE)),
                    TEMPLATE_PREVIEW_CUSTOMER_NAME,
                    TEMPLATE_PREVIEW_BILL_ID,
                    TEMPLATE_PREVIEW_TOTAL_AMOUNT,
                    pharmacyName,
                    TEMPLATE_PREVIEW_CARE_NOTE));
        }
        if (txtEmailSubjectPreview != null) {
            txtEmailSubjectPreview.setText(MessageTemplate.render(
                    valueOrDefault(txtEmailSubjectTemplate, messageTemplateDAO.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT)),
                    TEMPLATE_PREVIEW_CUSTOMER_NAME,
                    TEMPLATE_PREVIEW_BILL_ID,
                    TEMPLATE_PREVIEW_TOTAL_AMOUNT,
                    pharmacyName,
                    TEMPLATE_PREVIEW_CARE_NOTE));
        }
        if (txtEmailBodyPreview != null) {
            txtEmailBodyPreview.setText(MessageTemplate.render(
                    valueOrDefault(txtEmailBodyTemplate, messageTemplateDAO.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY)),
                    TEMPLATE_PREVIEW_CUSTOMER_NAME,
                    TEMPLATE_PREVIEW_BILL_ID,
                    TEMPLATE_PREVIEW_TOTAL_AMOUNT,
                    pharmacyName,
                    TEMPLATE_PREVIEW_CARE_NOTE));
        }
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

    private String resolveTemplateText(MessageTemplate template, String templateKey) {
        if (template == null) {
            return messageTemplateDAO.getDefaultBody(templateKey);
        }
        String value = template.getBodyTemplate();
        if ((value == null || value.isBlank()) && template.getSubjectTemplate() != null) {
            value = template.getSubjectTemplate();
        }
        if (value == null || value.isBlank()) {
            return messageTemplateDAO.getDefaultBody(templateKey);
        }
        return value;
    }

    private void browseReportTemplate(TextField targetField, String dialogTitle) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(dialogTitle);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JasperReports JRXML", "*.jrxml"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        String currentPath = targetField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            File parent = currentFile.isDirectory() ? currentFile : currentFile.getParentFile();
            if (parent != null && parent.exists()) {
                chooser.setInitialDirectory(parent);
            }
        }
        File selected = chooser.showOpenDialog(targetField.getScene().getWindow());
        if (selected != null) {
            targetField.setText(selected.getAbsolutePath());
        }
    }

    private String validateOptionalJrxmlPath(TextField field, String templateLabel) {
        String value = valueOrDefault(field, "");
        if (value.isBlank()) {
            return "";
        }
        File file = new File(value);
        if (!file.isFile()) {
            throw new IllegalArgumentException("The selected " + templateLabel + " template file was not found.");
        }
        String fileName = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!fileName.endsWith(".jrxml")) {
            throw new IllegalArgumentException("The " + templateLabel + " template must be a .jrxml file.");
        }
        return file.getAbsolutePath();
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
