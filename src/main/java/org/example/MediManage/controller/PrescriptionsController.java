package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.example.MediManage.dao.PrescriptionDAO;
import org.example.MediManage.model.Prescription;
import org.example.MediManage.service.DashboardKpiService;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.AsyncUiFeedback;

import java.util.Arrays;
import java.util.List;

public class PrescriptionsController {
    private static final String AI_VALIDATE_READY_LABEL = "🤖 Validate with AI";
    private static final String AI_VALIDATE_BUSY_LABEL = "⏳ Validating...";

    // ======================== FXML BINDINGS ========================

    @FXML
    private TableView<Prescription> prescriptionTable;
    @FXML
    private TableColumn<Prescription, Integer> colId;
    @FXML
    private TableColumn<Prescription, String> colCustomer;
    @FXML
    private TableColumn<Prescription, String> colDoctor;
    @FXML
    private TableColumn<Prescription, String> colMedicines;
    @FXML
    private TableColumn<Prescription, String> colStatus;
    @FXML
    private TableColumn<Prescription, String> colDate;

    @FXML
    private ComboBox<String> statusFilter;

    @FXML
    private TextField txtCustomerName;
    @FXML
    private TextField txtDoctorName;
    @FXML
    private TextArea txtMedicines;
    @FXML
    private TextArea txtNotes;
    @FXML
    private TextArea txtAIValidation;

    @FXML
    private Button btnSave;
    @FXML
    private Button btnAdvance;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnAIValidate;
    @FXML
    private ProgressIndicator spinnerAIValidation;

    // ======================== STATE ========================

    private final PrescriptionDAO prescriptionDAO = new PrescriptionDAO();
    private final AIAssistantService aiService = new AIAssistantService();
    private final ObservableList<Prescription> prescriptionList = FXCollections.observableArrayList();
    private Prescription selectedPrescription = null;
    private boolean keyboardShortcutsRegistered = false;

    // ======================== INIT ========================

    @FXML
    public void initialize() {
        // Table column bindings
        colId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getPrescriptionId()).asObject());
        colCustomer.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCustomerName()));
        colDoctor.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDoctorName()));
        colMedicines.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getMedicinesText()));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatus()));
        colDate.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPrescribedDate()));

        // Status column with color coding
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-pending", "status-verified", "status-dispensed");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    switch (item) {
                        case "PENDING":
                            getStyleClass().add("status-pending");
                            break;
                        case "VERIFIED":
                            getStyleClass().add("status-verified");
                            break;
                        case "DISPENSED":
                            getStyleClass().add("status-dispensed");
                            break;
                        default:
                            break;
                    }
                }
            }
        });

        prescriptionTable.setItems(prescriptionList);

        // Status filter ComboBox
        statusFilter.getItems().addAll("All", "PENDING", "VERIFIED", "DISPENSED");
        statusFilter.setValue("All");
        statusFilter.setOnAction(e -> handleRefresh());

        // Table selection -> populate form
        prescriptionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectPrescription(newVal);
            }
        });

        // Load data
        handleRefresh();
        setupKeyboardShortcuts();
    }

    // ======================== DATA ========================

    @FXML
    private void handleRefresh() {
        String filter = statusFilter.getValue();

        javafx.concurrent.Task<List<Prescription>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Prescription> call() {
                if (filter == null || "All".equals(filter)) {
                    return prescriptionDAO.getAllPrescriptions();
                } else {
                    return prescriptionDAO.getByStatus(filter);
                }
            }
        };

        task.setOnSucceeded(e -> prescriptionList.setAll(task.getValue()));
        task.setOnFailed(e -> System.err.println("Failed to load prescriptions: " + task.getException().getMessage()));

        AppExecutors.background().execute(task);
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = txtCustomerName == null ? null : txtCustomerName.getScene();
            if (keyboardShortcutsRegistered || scene == null) {
                return;
            }
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcut);
            keyboardShortcutsRegistered = true;
        });
    }

    private void handleKeyboardShortcut(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.S) {
            handleSave();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.R) {
            handleRefresh();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
            handleAIValidate();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ESCAPE) {
            handleClear();
            event.consume();
        }
    }

    // ======================== FORM ========================

    private void selectPrescription(Prescription p) {
        selectedPrescription = p;
        txtCustomerName.setText(p.getCustomerName());
        txtDoctorName.setText(p.getDoctorName());
        txtMedicines.setText(p.getMedicinesText());
        txtNotes.setText(p.getNotes());
        txtAIValidation.setText(p.getAiValidation());

        btnSave.setText("Update Prescription");
        btnDelete.setDisable(false);

        // Enable advance button if not yet DISPENSED
        btnAdvance.setDisable("DISPENSED".equals(p.getStatus()));

        // Show next status on button
        String nextStatus = getNextStatus(p.getStatus());
        if (nextStatus != null) {
            btnAdvance.setText("▶ Mark " + nextStatus);
        }
    }

    private String getNextStatus(String current) {
        if ("PENDING".equals(current))
            return "VERIFIED";
        if ("VERIFIED".equals(current))
            return "DISPENSED";
        return null;
    }

    // ======================== ACTIONS ========================

    @FXML
    private void handleSave() {
        String customerName = txtCustomerName.getText();
        String medicines = txtMedicines.getText();

        if (customerName == null || customerName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Customer name is required.");
            return;
        }
        if (medicines == null || medicines.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Medicines list is required.");
            return;
        }

        try {
            Prescription p = new Prescription();
            p.setCustomerName(customerName.trim());
            p.setDoctorName(txtDoctorName.getText());
            p.setMedicinesText(medicines.trim());
            p.setNotes(txtNotes.getText());

            if (selectedPrescription != null) {
                // For update: delete old and re-add
                prescriptionDAO.deletePrescription(selectedPrescription.getPrescriptionId());
            }

            prescriptionDAO.addPrescription(p);
            DashboardKpiService.invalidatePrescriptionMetrics();
            showAlert(Alert.AlertType.INFORMATION, "Prescription saved successfully.");
            handleClear();
            handleRefresh();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error saving prescription: " + ex.getMessage());
        }
    }

    @FXML
    private void handleAdvanceStatus() {
        if (selectedPrescription == null)
            return;

        String nextStatus = getNextStatus(selectedPrescription.getStatus());
        if (nextStatus == null)
            return;

        try {
            prescriptionDAO.updateStatus(selectedPrescription.getPrescriptionId(), nextStatus);
            DashboardKpiService.invalidatePrescriptionMetrics();
            showAlert(Alert.AlertType.INFORMATION, "Status updated to " + nextStatus);
            handleClear();
            handleRefresh();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error updating status: " + ex.getMessage());
        }
    }

    @FXML
    private void handleAIValidate() {
        String medicines = txtMedicines.getText();
        if (medicines == null || medicines.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Enter medicines to validate.");
            return;
        }

        AsyncUiFeedback.showLoading(btnAIValidate, spinnerAIValidation, txtAIValidation,
                AI_VALIDATE_BUSY_LABEL, "⏳ Running prescription safety checks...");

        try {
            aiService.validatePrescription(parseMedicines(medicines))
                    .thenAccept(response -> Platform.runLater(() -> {
                        AsyncUiFeedback.showSuccess(btnAIValidate, spinnerAIValidation, txtAIValidation,
                                AI_VALIDATE_READY_LABEL, response);
                        if (selectedPrescription != null) {
                            try {
                                prescriptionDAO.saveAIValidation(selectedPrescription.getPrescriptionId(), response);
                            } catch (Exception ex) {
                                System.err.println("Failed to save AI validation: " + ex.getMessage());
                            }
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> AsyncUiFeedback.showError(btnAIValidate, spinnerAIValidation,
                                txtAIValidation, AI_VALIDATE_READY_LABEL, ex));
                        return null;
                    });
        } catch (Exception ex) {
            AsyncUiFeedback.showError(btnAIValidate, spinnerAIValidation, txtAIValidation,
                    AI_VALIDATE_READY_LABEL, ex);
        }
    }

    private List<String> parseMedicines(String medicinesText) {
        return Arrays.stream(medicinesText.split("[,\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @FXML
    private void handleDelete() {
        if (selectedPrescription == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete prescription #" + selectedPrescription.getPrescriptionId() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Delete");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    prescriptionDAO.deletePrescription(selectedPrescription.getPrescriptionId());
                    DashboardKpiService.invalidatePrescriptionMetrics();
                    handleClear();
                    handleRefresh();
                    showAlert(Alert.AlertType.INFORMATION, "Prescription deleted.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Error deleting: " + ex.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleClear() {
        selectedPrescription = null;
        txtCustomerName.clear();
        txtDoctorName.clear();
        txtMedicines.clear();
        txtNotes.clear();
        txtAIValidation.clear();
        if (btnAIValidate != null) {
            btnAIValidate.setDisable(false);
            btnAIValidate.setText(AI_VALIDATE_READY_LABEL);
        }
        if (spinnerAIValidation != null) {
            spinnerAIValidation.setVisible(false);
            spinnerAIValidation.setManaged(false);
        }

        btnSave.setText("Add Prescription");
        btnAdvance.setDisable(true);
        btnAdvance.setText("▶ Advance Status");
        btnDelete.setDisable(true);
        prescriptionTable.getSelectionModel().clearSelection();
    }

    // ======================== UTILS ========================

    private void showAlert(Alert.AlertType type, String content) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
