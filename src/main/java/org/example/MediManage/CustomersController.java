package org.example.MediManage;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.example.MediManage.model.Customer;
import org.example.MediManage.service.CustomerService;
import javafx.application.Platform;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.AsyncUiFeedback;

import java.util.List;

public class CustomersController {
    private static final String ANALYZE_READY_LABEL = "✨ Analyze Customer Health";
    private static final String ANALYZE_BUSY_LABEL = "⏳ Analyzing...";

    // ======================== FXML BINDINGS ========================

    @FXML
    private TableView<Customer> customerTable;
    @FXML
    private TableColumn<Customer, Integer> colId;
    @FXML
    private TableColumn<Customer, String> colName;
    @FXML
    private TableColumn<Customer, String> colPhone;
    @FXML
    private TableColumn<Customer, String> colEmail;
    @FXML
    private TableColumn<Customer, String> colDiseases;
    @FXML
    private TableColumn<Customer, Double> colBalance;

    @FXML
    private TextField searchField;
    @FXML
    private TextField txtName;
    @FXML
    private TextField txtPhone;
    @FXML
    private TextField txtEmail;
    @FXML
    private TextField txtAddress;
    @FXML
    private TextField txtNomineeName;
    @FXML
    private TextField txtNomineeRelation;
    @FXML
    private TextField txtInsuranceProvider;
    @FXML
    private TextField txtInsurancePolicyNo;
    @FXML
    private TextArea txtDiseases;

    @FXML
    private Button btnSave;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnAIAnalysis;
    @FXML
    private TextArea txtAIAnalysis;
    @FXML
    private ProgressIndicator spinnerAnalysis;

    // ======================== STATE ========================

    private final CustomerService customerService = new CustomerService();
    private final ObservableList<Customer> customerList = FXCollections.observableArrayList();
    private FilteredList<Customer> filteredList;
    private Customer selectedCustomer = null;
    private boolean keyboardShortcutsRegistered = false;

    // ======================== INIT ========================

    @FXML
    public void initialize() {
        // Column bindings
        colId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getCustomerId()).asObject());
        colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        colPhone.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPhoneNumber()));
        colEmail.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getEmail()));
        colDiseases.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDiseases()));
        colBalance.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getCurrentBalance()).asObject());

        // Balance column formatting
        colBalance.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("balance-outstanding", "balance-clear");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("₹ %.2f", item));
                    if (item > 0) {
                        getStyleClass().add("balance-outstanding");
                    } else {
                        getStyleClass().add("balance-clear");
                    }
                }
            }
        });

        // Filtered list for search
        filteredList = new FilteredList<>(customerList, p -> true);
        customerTable.setItems(filteredList);

        // Search listener
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredList.setPredicate(customer -> customerService.matchesSearch(customer, newVal));
        });

        // Table selection listener → populate form
        customerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectCustomer(newVal);
            }
        });

        // Load data
        loadCustomers();
        setupKeyboardShortcuts();
    }

    // ======================== DATA ========================

    private void loadCustomers() {
        javafx.concurrent.Task<List<Customer>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Customer> call() {
                return customerService.getAllCustomers();
            }
        };

        task.setOnSucceeded(e -> {
            customerList.setAll(task.getValue());
        });
        task.setOnFailed(e -> {
            System.err.println("Failed to load customers: " + task.getException().getMessage());
        });

        AppExecutors.background().execute(task);
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = searchField == null ? null : searchField.getScene();
            if (keyboardShortcutsRegistered || scene == null) {
                return;
            }
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcut);
            keyboardShortcutsRegistered = true;
        });
    }

    private void handleKeyboardShortcut(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.F) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.S) {
            handleSave();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.ENTER && btnAIAnalysis != null
                && !btnAIAnalysis.isDisable()) {
            handleAIAnalysis();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ESCAPE) {
            handleClear();
            event.consume();
        }
    }

    // ======================== FORM ========================

    private void selectCustomer(Customer c) {
        selectedCustomer = c;
        txtName.setText(c.getName());
        txtPhone.setText(c.getPhoneNumber());
        txtEmail.setText(c.getEmail());
        txtAddress.setText(c.getAddress());
        txtNomineeName.setText(c.getNomineeName());
        txtNomineeRelation.setText(c.getNomineeRelation());
        txtInsuranceProvider.setText(c.getInsuranceProvider());
        txtInsurancePolicyNo.setText(c.getInsurancePolicyNo());
        txtDiseases.setText(c.getDiseases());

        btnSave.setText("Update Customer");
        btnDelete.setDisable(false);
        if (btnAIAnalysis != null)
            btnAIAnalysis.setDisable(false);
    }

    private Customer buildCustomerFromForm() {
        Customer c = new Customer();
        c.setName(txtName.getText());
        c.setPhoneNumber(txtPhone.getText());
        c.setEmail(txtEmail.getText());
        c.setAddress(txtAddress.getText());
        c.setNomineeName(txtNomineeName.getText());
        c.setNomineeRelation(txtNomineeRelation.getText());
        c.setInsuranceProvider(txtInsuranceProvider.getText());
        c.setInsurancePolicyNo(txtInsurancePolicyNo.getText());
        c.setDiseases(txtDiseases.getText());
        return c;
    }

    // ======================== ACTIONS ========================

    @FXML
    private void handleSave() {
        String name = txtName.getText();
        String phone = txtPhone.getText();

        String validationError = customerService.validateRequiredFields(name, phone);
        if (validationError != null) {
            showAlert(Alert.AlertType.WARNING, validationError);
            return;
        }

        try {
            Customer c = buildCustomerFromForm();
            CustomerService.SaveResult saveResult = customerService.saveCustomer(c, selectedCustomer);
            showAlert(Alert.AlertType.INFORMATION, saveResult.message());
            handleClear();
            loadCustomers();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error saving customer: " + ex.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedCustomer == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete customer \"" + selectedCustomer.getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Delete");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    customerService.deleteCustomer(selectedCustomer);
                    handleClear();
                    loadCustomers();
                    showAlert(Alert.AlertType.INFORMATION, "Customer deleted.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Error deleting customer: " + ex.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleClear() {
        selectedCustomer = null;
        txtName.clear();
        txtPhone.clear();
        txtEmail.clear();
        txtAddress.clear();
        txtNomineeName.clear();
        txtNomineeRelation.clear();
        txtInsuranceProvider.clear();
        txtInsurancePolicyNo.clear();
        txtDiseases.clear();

        btnSave.setText("Add Customer");
        btnDelete.setDisable(true);
        if (btnAIAnalysis != null)
            btnAIAnalysis.setDisable(true);
        if (txtAIAnalysis != null)
            txtAIAnalysis.clear();
        customerTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleAIAnalysis() {
        String diseases = txtDiseases.getText();
        CustomerService.HealthAnalysisPreparation analysis = customerService.prepareHealthAnalysis(selectedCustomer,
                diseases);
        if (!analysis.canProceed()) {
            if (txtAIAnalysis != null)
                txtAIAnalysis.setText(analysis.message());
            return;
        }

        AsyncUiFeedback.showLoading(btnAIAnalysis, spinnerAnalysis, txtAIAnalysis,
                ANALYZE_BUSY_LABEL, "⏳ Running AI health analysis...");

        customerService.analyzeCustomerHealth(selectedCustomer, analysis.diseases())
                .thenAccept(result -> Platform.runLater(() -> {
                    AsyncUiFeedback.showSuccess(btnAIAnalysis, spinnerAnalysis, txtAIAnalysis,
                            ANALYZE_READY_LABEL, result);
                })).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        AsyncUiFeedback.showError(btnAIAnalysis, spinnerAnalysis, txtAIAnalysis,
                                ANALYZE_READY_LABEL, ex);
                    });
                    return null;
                });
    }

    // ======================== UTILS ========================

    private void showAlert(Alert.AlertType type, String content) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
