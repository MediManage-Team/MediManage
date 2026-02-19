package org.example.MediManage;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.model.Customer;
import org.example.MediManage.service.ai.AIAssistantService;
import javafx.application.Platform;

import java.util.List;

public class CustomersController {

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

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AIAssistantService aiService = new AIAssistantService();
    private final ObservableList<Customer> customerList = FXCollections.observableArrayList();
    private FilteredList<Customer> filteredList;
    private Customer selectedCustomer = null;

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
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("₹ %.2f", item));
                    if (item > 0) {
                        setStyle("-fx-text-fill: #e74c3c;"); // Red for outstanding balance
                    } else {
                        setStyle("-fx-text-fill: #2ecc71;"); // Green for clear/credit
                    }
                }
            }
        });

        // Filtered list for search
        filteredList = new FilteredList<>(customerList, p -> true);
        customerTable.setItems(filteredList);

        // Search listener
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String lower = newVal == null ? "" : newVal.toLowerCase().trim();
            filteredList.setPredicate(customer -> {
                if (lower.isEmpty())
                    return true;
                return (customer.getName() != null && customer.getName().toLowerCase().contains(lower)) ||
                        (customer.getPhoneNumber() != null && customer.getPhoneNumber().contains(lower)) ||
                        (customer.getEmail() != null && customer.getEmail().toLowerCase().contains(lower));
            });
        });

        // Table selection listener → populate form
        customerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectCustomer(newVal);
            }
        });

        // Load data
        loadCustomers();
    }

    // ======================== DATA ========================

    private void loadCustomers() {
        javafx.concurrent.Task<List<Customer>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Customer> call() {
                return customerDAO.getAllCustomers();
            }
        };

        task.setOnSucceeded(e -> {
            customerList.setAll(task.getValue());
        });
        task.setOnFailed(e -> {
            System.err.println("Failed to load customers: " + task.getException().getMessage());
        });

        new Thread(task).start();
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

        if (name == null || name.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Name is required.");
            return;
        }
        if (phone == null || phone.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Phone number is required.");
            return;
        }

        try {
            if (selectedCustomer != null) {
                // Update
                Customer c = buildCustomerFromForm();
                c.setCustomerId(selectedCustomer.getCustomerId());
                customerDAO.updateCustomer(c);
                showAlert(Alert.AlertType.INFORMATION, "Customer updated successfully.");
            } else {
                // Add new
                Customer c = buildCustomerFromForm();
                customerDAO.addCustomer(c);
                showAlert(Alert.AlertType.INFORMATION, "Customer added successfully.");
            }
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
                    customerDAO.deleteCustomer(selectedCustomer.getCustomerId());
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
        if (selectedCustomer == null) {
            if (txtAIAnalysis != null)
                txtAIAnalysis.setText("Please select a customer first.");
            return;
        }

        String diseases = txtDiseases.getText();
        if (diseases == null || diseases.trim().isEmpty()) {
            if (txtAIAnalysis != null)
                txtAIAnalysis.setText("No known conditions listed. Add conditions to get a health analysis.");
            return;
        }

        if (txtAIAnalysis != null)
            txtAIAnalysis.setText("Analyzing health profile with AI...");
        if (btnAIAnalysis != null)
            btnAIAnalysis.setDisable(true);
        if (spinnerAnalysis != null) {
            spinnerAnalysis.setVisible(true);
            spinnerAnalysis.setManaged(true);
        }

        aiService.analyzeCustomerHistory(
                selectedCustomer.getCustomerId(),
                selectedCustomer.getName(),
                diseases).thenAccept(result -> Platform.runLater(() -> {
                    if (txtAIAnalysis != null)
                        txtAIAnalysis.setText(result);
                    if (btnAIAnalysis != null)
                        btnAIAnalysis.setDisable(false);
                    if (spinnerAnalysis != null) {
                        spinnerAnalysis.setVisible(false);
                        spinnerAnalysis.setManaged(false);
                    }
                })).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        if (txtAIAnalysis != null)
                            txtAIAnalysis.setText("Error: " + ex.getMessage());
                        if (btnAIAnalysis != null)
                            btnAIAnalysis.setDisable(false);
                        if (spinnerAnalysis != null) {
                            spinnerAnalysis.setVisible(false);
                            spinnerAnalysis.setManaged(false);
                        }
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
