package org.example.MediManage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Medicine;

import java.time.LocalDate;
import java.util.Optional;

public class InventoryController {

    @FXML
    private TextField searchField;
    @FXML
    private TableView<Medicine> inventoryTable;
    @FXML
    private TableColumn<Medicine, Integer> colId;
    @FXML
    private TableColumn<Medicine, String> colName;
    @FXML
    private TableColumn<Medicine, String> colCompany;
    @FXML
    private TableColumn<Medicine, String> colExpiry;
    @FXML
    private TableColumn<Medicine, Integer> colStock;
    @FXML
    private TableColumn<Medicine, Double> colPrice;

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtCompany;
    @FXML
    private DatePicker dateExpiry;
    @FXML
    private TextField txtPrice;
    @FXML
    private TextField txtStock;

    @FXML
    private Button btnSave;
    @FXML
    private Button btnDelete;

    private MedicineDAO medicineDAO = new MedicineDAO();
    private ObservableList<Medicine> masterData = FXCollections.observableArrayList();
    private Medicine selectedMedicine = null;

    @FXML
    public void initialize() {
        setupTable();
        loadData();
        setupSearch();
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCompany.setCellValueFactory(new PropertyValueFactory<>("company"));
        colExpiry.setCellValueFactory(new PropertyValueFactory<>("expiry"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));

        // Row Factory for Low Stock Alert
        inventoryTable.setRowFactory(tv -> new TableRow<Medicine>() {
            @Override
            protected void updateItem(Medicine item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.getStock() < 10) {
                    setStyle("-fx-background-color: #ffcccc;"); // Light red for low stock
                } else {
                    setStyle("");
                }
            }
        });

        inventoryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                populateForm(newVal);
            }
        });
    }

    private void loadData() {
        masterData.clear();
        masterData.addAll(medicineDAO.getAllMedicines());
        inventoryTable.setItems(masterData);
        inventoryTable.refresh();
    }

    private void setupSearch() {
        FilteredList<Medicine> filteredData = new FilteredList<>(masterData, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(medicine -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();

                if (medicine.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (medicine.getCompany().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        });

        inventoryTable.setItems(filteredData);
    }

    private void populateForm(Medicine med) {
        selectedMedicine = med;
        txtName.setText(med.getName());
        txtCompany.setText(med.getCompany());
        if (med.getExpiry() != null && !med.getExpiry().isEmpty()) {
            try {
                dateExpiry.setValue(LocalDate.parse(med.getExpiry()));
            } catch (Exception e) {
                dateExpiry.setValue(null);
            }
        }
        txtPrice.setText(String.valueOf(med.getPrice()));
        txtStock.setText(String.valueOf(med.getStock()));

        btnSave.setText("Update Medicine");
        btnDelete.setDisable(false);
    }

    @FXML
    private void handleSave() {
        String name = txtName.getText();
        String company = txtCompany.getText();
        LocalDate expiryDate = dateExpiry.getValue();
        String priceStr = txtPrice.getText();
        String stockStr = txtStock.getText();

        if (name.isEmpty() || company.isEmpty() || expiryDate == null || priceStr.isEmpty() || stockStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "All fields are required.");
            return;
        }

        try {
            double price = Double.parseDouble(priceStr);
            int stock = Integer.parseInt(stockStr);
            String expiry = expiryDate.toString();

            if (selectedMedicine == null) {
                // Add New
                medicineDAO.addMedicine(name, company, expiry, price, stock);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Medicine added.");
            } else {
                // Update Existing
                selectedMedicine.setName(name);
                selectedMedicine.setCompany(company);
                selectedMedicine.setExpiry(expiry);
                selectedMedicine.setPrice(price);
                // Stock update is separate in DAO but for UX we do it here too via separate
                // call or assuming updateMedicine usually doesn't update stock count in generic
                // CRUD but requirement said "updateStock" method exists.
                // We should call both updateMedicine and updateStock to be safe.
                medicineDAO.updateMedicine(selectedMedicine);
                medicineDAO.updateStock(selectedMedicine.getId(), stock);

                showAlert(Alert.AlertType.INFORMATION, "Success", "Medicine updated.");
            }
            handleClear();
            loadData();

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Price must be a number and Stock must be an integer.");
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedMedicine == null)
            return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Medicine");
        alert.setContentText("Delete " + selectedMedicine.getName() + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            medicineDAO.deleteMedicine(selectedMedicine.getId());
            handleClear();
            loadData();
        }
    }

    @FXML
    private void handleClear() {
        selectedMedicine = null;
        txtName.clear();
        txtCompany.clear();
        dateExpiry.setValue(null);
        txtPrice.clear();
        txtStock.clear();
        inventoryTable.getSelectionModel().clearSelection();
        btnSave.setText("Add New");
        btnDelete.setDisable(true);
    }

    @FXML
    private void handleExportExcel() {
        // Placeholder for future implementation
        showAlert(Alert.AlertType.INFORMATION, "Export", "Export to Excel not implemented yet.");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
