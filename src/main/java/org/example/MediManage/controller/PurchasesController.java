package org.example.MediManage.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.PurchaseOrderDAO;
import org.example.MediManage.dao.SupplierDAO;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.PurchaseOrder;
import org.example.MediManage.model.PurchaseOrderItem;
import org.example.MediManage.model.Supplier;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.util.List;

public class PurchasesController {

    // --- Order Tab ---
    @FXML private TableView<PurchaseOrderItem> poTable;
    @FXML private TableColumn<PurchaseOrderItem, String> colMedicine;
    @FXML private TableColumn<PurchaseOrderItem, String> colCompany;
    @FXML private TableColumn<PurchaseOrderItem, Number> colQty;
    @FXML private TableColumn<PurchaseOrderItem, Number> colCost;
    @FXML private TableColumn<PurchaseOrderItem, Number> colTotal;
    @FXML private Label lblTotalAmount;

    @FXML private ComboBox<Supplier> comboSupplier;
    @FXML private TextField txtNotes;
    
    @FXML private TextField txtSearchMedicine;
    @FXML private ListView<Medicine> listMedicineSuggestions;
    @FXML private TextField txtQty;
    @FXML private TextField txtCost;

    @FXML private Button btnReceiveOrder;

    // --- History Tab ---
    @FXML private TableView<PurchaseOrder> historyTable;
    @FXML private TableColumn<PurchaseOrder, Number> histColId;
    @FXML private TableColumn<PurchaseOrder, String> histColDate;
    @FXML private TableColumn<PurchaseOrder, String> histColSupplier;
    @FXML private TableColumn<PurchaseOrder, Number> histColAmount;
    @FXML private TableColumn<PurchaseOrder, String> histColStatus;

    private final PurchaseOrderDAO poDAO = new PurchaseOrderDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final MedicineDAO medicineDAO = new MedicineDAO();

    private final ObservableList<PurchaseOrderItem> cartItems = FXCollections.observableArrayList();
    private final ObservableList<PurchaseOrder> historyItems = FXCollections.observableArrayList();
    
    private Medicine selectedMedicine = null;

    @FXML
    public void initialize() {
        setupCartTable();
        setupHistoryTable();
        setupSupplierCombo();
        setupMedicineAutocomplete();
        
        loadHistory();
    }

    private void setupCartTable() {
        colMedicine.setCellValueFactory(d -> d.getValue().medicineNameProperty());
        colCompany.setCellValueFactory(d -> d.getValue().companyProperty());
        colQty.setCellValueFactory(d -> d.getValue().receivedQtyProperty());
        colCost.setCellValueFactory(d -> d.getValue().unitCostProperty());
        colTotal.setCellValueFactory(d -> d.getValue().totalCostProperty());
        poTable.setItems(cartItems);
    }
    
    private void setupHistoryTable() {
        histColId.setCellValueFactory(d -> d.getValue().poIdProperty());
        histColDate.setCellValueFactory(d -> d.getValue().orderDateProperty());
        histColSupplier.setCellValueFactory(d -> d.getValue().supplierNameProperty());
        histColAmount.setCellValueFactory(d -> d.getValue().totalAmountProperty());
        histColStatus.setCellValueFactory(d -> d.getValue().statusProperty());
        historyTable.setItems(historyItems);
        
        historyTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !historyTable.getSelectionModel().isEmpty()) {
                showPurchaseOrderDetails(historyTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupSupplierCombo() {
        try {
            List<Supplier> activeSuppliers = supplierDAO.getActiveSuppliers();
            comboSupplier.setItems(FXCollections.observableArrayList(activeSuppliers));
            comboSupplier.setCellFactory(lv -> new ListCell<Supplier>() {
                @Override
                protected void updateItem(Supplier item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.getName());
                }
            });
            comboSupplier.setButtonCell(new ListCell<Supplier>() {
                @Override
                protected void updateItem(Supplier item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.getName());
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupMedicineAutocomplete() {
        txtSearchMedicine.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.length() < 2) {
                listMedicineSuggestions.setVisible(false);
                return;
            }
            try {
                List<Medicine> results = medicineDAO.searchMedicines(newValue, 0, 15);
                listMedicineSuggestions.setItems(FXCollections.observableArrayList(results));
                
                listMedicineSuggestions.setCellFactory(lv -> new ListCell<Medicine>() {
                    @Override
                    protected void updateItem(Medicine item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(item.getName() + " (" + item.getCompany() + ")");
                        }
                    }
                });
                
                listMedicineSuggestions.setVisible(!results.isEmpty());
                if (!results.isEmpty()) {
                    listMedicineSuggestions.setPrefHeight(Math.min(150, results.size() * 25));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        listMedicineSuggestions.setOnMouseClicked(event -> {
            selectedMedicine = listMedicineSuggestions.getSelectionModel().getSelectedItem();
            if (selectedMedicine != null) {
                txtSearchMedicine.setText(selectedMedicine.getName());
                txtCost.setText(String.valueOf(selectedMedicine.getPrice() * 0.7)); // Default guesstimate cost
                listMedicineSuggestions.setVisible(false);
                txtQty.requestFocus();
            }
        });
    }

    @FXML
    private void handleAddItem() {
        if (selectedMedicine == null) {
            showAlert(Alert.AlertType.WARNING, "Select Medicine", "Please search and select a medicine from the list.");
            return;
        }
        
        try {
            int qty = Integer.parseInt(txtQty.getText());
            double cost = Double.parseDouble(txtCost.getText());
            
            if (qty <= 0) throw new NumberFormatException("Quantity must be positive.");
            if (cost < 0) throw new NumberFormatException("Cost cannot be negative.");
            
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setMedicineId(selectedMedicine.getId());
            item.setMedicineName(selectedMedicine.getName());
            item.setCompany(selectedMedicine.getCompany());
            item.setOrderedQty(qty);
            item.setReceivedQty(qty);
            item.setUnitCost(cost);
            
            cartItems.add(item);
            updateTotal();
            
            // clear inputs
            selectedMedicine = null;
            txtSearchMedicine.clear();
            txtQty.clear();
            txtCost.clear();
            txtSearchMedicine.requestFocus();
            
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter valid numbers for Quantity and Cost.");
        }
    }

    @FXML
    private void handleRemoveItem() {
        PurchaseOrderItem selected = poTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            cartItems.remove(selected);
            updateTotal();
        } else {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an item to remove.");
        }
    }

    private void updateTotal() {
        double total = cartItems.stream().mapToDouble(PurchaseOrderItem::getTotalCost).sum();
        lblTotalAmount.setText(String.format("₹ %.2f", total));
    }

    @FXML
    private void handleReceiveOrder() {
        Supplier supplier = comboSupplier.getValue();
        if (supplier == null) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a Supplier.");
            return;
        }
        if (cartItems.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Cannot receive an empty order.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Receive this order and update inventory stock?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    double total = cartItems.stream().mapToDouble(PurchaseOrderItem::getTotalCost).sum();
                    
                    PurchaseOrder po = new PurchaseOrder();
                    po.setSupplierId(supplier.getSupplierId());
                    po.setTotalAmount(total);
                    po.setNotes(txtNotes.getText());
                    po.setCreatedByUserId(UserSession.getInstance().getUser() != null ? UserSession.getInstance().getUser().getId() : 0);
                    
                    // Transactional save and stock update
                    poDAO.receivePurchaseOrder(po, cartItems);
                    
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Purchase Order received and stock updated!");
                    
                    // Reset
                    cartItems.clear();
                    comboSupplier.setValue(null);
                    txtNotes.clear();
                    updateTotal();
                    loadHistory();
                    
                } catch (SQLException e) {
                    e.printStackTrace();
                    showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save Purchase Order: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefreshHistory() {
        loadHistory();
    }

    private void loadHistory() {
        try {
            historyItems.setAll(poDAO.getAllPurchaseOrders());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void showPurchaseOrderDetails(PurchaseOrder po) {
        if (po == null) return;
        
        try {
            List<PurchaseOrderItem> items = poDAO.getPurchaseOrderItems(po.getPoId());
            
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("PO Details - #" + po.getPoId());
            dialog.setHeaderText("Supplier: " + po.getSupplierName() + "\nDate: " + po.getOrderDate());
            
            DialogPane pane = dialog.getDialogPane();
            pane.getButtonTypes().add(ButtonType.CLOSE);
            pane.setPrefWidth(500);
            
            TableView<PurchaseOrderItem> table = new TableView<>();
            table.setItems(FXCollections.observableArrayList(items));
            
            TableColumn<PurchaseOrderItem, String> colMed = new TableColumn<>("Medicine");
            colMed.setCellValueFactory(d -> d.getValue().medicineNameProperty());
            
            TableColumn<PurchaseOrderItem, Number> colQ = new TableColumn<>("Qty");
            colQ.setCellValueFactory(d -> d.getValue().receivedQtyProperty());
            
            TableColumn<PurchaseOrderItem, Number> colC = new TableColumn<>("Cost");
            colC.setCellValueFactory(d -> d.getValue().unitCostProperty());
            
            TableColumn<PurchaseOrderItem, Number> colT = new TableColumn<>("Total");
            colT.setCellValueFactory(d -> d.getValue().totalCostProperty());
            
            table.getColumns().add(colMed);
            table.getColumns().add(colQ);
            table.getColumns().add(colC);
            table.getColumns().add(colT);
            
            pane.setContent(table);
            dialog.showAndWait();
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load PO details.");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
