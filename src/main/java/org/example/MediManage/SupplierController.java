package org.example.MediManage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.MediManage.dao.SupplierDAO;
import org.example.MediManage.model.Supplier;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Controller for the Supplier Management view.
 */
public class SupplierController {

    @FXML
    private TableView<Supplier> supplierTable;
    @FXML
    private TableColumn<Supplier, Number> colId;
    @FXML
    private TableColumn<Supplier, String> colName;
    @FXML
    private TableColumn<Supplier, String> colContact;
    @FXML
    private TableColumn<Supplier, String> colPhone;
    @FXML
    private TableColumn<Supplier, String> colEmail;
    @FXML
    private TableColumn<Supplier, String> colGst;
    @FXML
    private TableColumn<Supplier, String> colStatus;
    @FXML
    private TextField txtSearch;

    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(cd -> cd.getValue().supplierIdProperty());
        colName.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colContact.setCellValueFactory(cd -> cd.getValue().contactPersonProperty());
        colPhone.setCellValueFactory(cd -> cd.getValue().phoneProperty());
        colEmail.setCellValueFactory(cd -> cd.getValue().emailProperty());
        colGst.setCellValueFactory(cd -> cd.getValue().gstNumberProperty());
        colStatus.setCellValueFactory(cd -> {
            boolean active = cd.getValue().isActive();
            return new javafx.beans.property.SimpleStringProperty(active ? "Active" : "Inactive");
        });

        supplierTable.setItems(suppliers);
        loadSuppliers();
    }

    private void loadSuppliers() {
        try {
            suppliers.setAll(supplierDAO.getAllSuppliers());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Load Error", e.getMessage());
        }
    }

    @FXML
    private void handleSearch() {
        String keyword = txtSearch.getText();
        if (keyword == null || keyword.isBlank()) {
            loadSuppliers();
            return;
        }
        try {
            suppliers.setAll(supplierDAO.searchSuppliers(keyword.trim()));
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Search Error", e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        Supplier newSupplier = showSupplierDialog(null);
        if (newSupplier == null)
            return;
        try {
            int id = supplierDAO.addSupplier(newSupplier);
            if (id > 0) {
                showAlert(Alert.AlertType.INFORMATION, "Supplier Added",
                        "Supplier '" + newSupplier.getName() + "' added (ID: " + id + ").");
                loadSuppliers();
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Add Error", e.getMessage());
        }
    }

    @FXML
    private void handleEdit() {
        Supplier selected = supplierTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select a supplier to edit.");
            return;
        }
        Supplier updated = showSupplierDialog(selected);
        if (updated == null)
            return;
        try {
            supplierDAO.updateSupplier(updated);
            showAlert(Alert.AlertType.INFORMATION, "Updated", "Supplier '" + updated.getName() + "' updated.");
            loadSuppliers();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Update Error", e.getMessage());
        }
    }

    @FXML
    private void handleDeactivate() {
        Supplier selected = supplierTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select a supplier to deactivate.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deactivate supplier '" + selected.getName() + "'?", ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK)
            return;

        try {
            supplierDAO.deactivateSupplier(selected.getSupplierId());
            showAlert(Alert.AlertType.INFORMATION, "Deactivated", "Supplier deactivated.");
            loadSuppliers();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        txtSearch.clear();
        loadSuppliers();
    }

    /**
     * Shows a form dialog for adding or editing a supplier. Returns null if
     * cancelled.
     */
    private Supplier showSupplierDialog(Supplier existing) {
        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Supplier" : "Edit Supplier");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        TextField contactField = new TextField(existing != null ? existing.getContactPerson() : "");
        TextField phoneField = new TextField(existing != null ? existing.getPhone() : "");
        TextField emailField = new TextField(existing != null ? existing.getEmail() : "");
        TextField addressField = new TextField(existing != null ? existing.getAddress() : "");
        TextField gstField = new TextField(existing != null ? existing.getGstNumber() : "");

        grid.add(new Label("Name *:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Contact Person:"), 0, 1);
        grid.add(contactField, 1, 1);
        grid.add(new Label("Phone:"), 0, 2);
        grid.add(phoneField, 1, 2);
        grid.add(new Label("Email:"), 0, 3);
        grid.add(emailField, 1, 3);
        grid.add(new Label("Address:"), 0, 4);
        grid.add(addressField, 1, 4);
        grid.add(new Label("GST #:"), 0, 5);
        grid.add(gstField, 1, 5);

        dialog.getDialogPane().setContent(grid);
        nameField.requestFocus();

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Validation", "Supplier name is required.");
                    return null;
                }
                Supplier s = existing != null ? existing : new Supplier();
                s.setName(name);
                s.setContactPerson(contactField.getText().trim());
                s.setPhone(phoneField.getText().trim());
                s.setEmail(emailField.getText().trim());
                s.setAddress(addressField.getText().trim());
                s.setGstNumber(gstField.getText().trim());
                return s;
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
