package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.PurchaseOrderDAO;
import org.example.MediManage.dao.SupplierDAO;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.PurchaseOrder;
import org.example.MediManage.model.PurchaseOrderItem;
import org.example.MediManage.model.Supplier;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.NavigationGuard;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.SupervisorApprovalDialogs;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PurchasesController implements NavigationGuard {
    private static final Logger LOGGER = Logger.getLogger(PurchasesController.class.getName());
    private static final String DEFAULT_EXISTING_HINT =
            "Select an existing medicine to prefill the latest known selling price, cost price, and expiry snapshot.";

    // --- Order Tab ---
    @FXML private TableView<PurchaseOrderItem> poTable;
    @FXML private TableColumn<PurchaseOrderItem, String> colMedicine;
    @FXML private TableColumn<PurchaseOrderItem, String> colCompany;
    @FXML private TableColumn<PurchaseOrderItem, String> colBatch;
    @FXML private TableColumn<PurchaseOrderItem, String> colExpiry;
    @FXML private TableColumn<PurchaseOrderItem, Number> colQty;
    @FXML private TableColumn<PurchaseOrderItem, Number> colCost;
    @FXML private TableColumn<PurchaseOrderItem, Number> colSell;
    @FXML private TableColumn<PurchaseOrderItem, Number> colTotal;
    @FXML private Label lblTotalAmount;

    @FXML private ComboBox<Supplier> comboSupplier;
    @FXML private TextField txtNotes;

    @FXML private CheckBox chkNewMedicine;
    @FXML private VBox existingMedicineBox;
    @FXML private VBox newMedicineBox;
    @FXML private TextField txtSearchMedicine;
    @FXML private ListView<Medicine> listMedicineSuggestions;
    @FXML private Label lblMedicineContext;
    @FXML private TextField txtNewMedicineName;
    @FXML private TextField txtNewGenericName;
    @FXML private TextField txtNewCompany;
    @FXML private TextField txtNewReorderThreshold;

    @FXML private TextField txtBatchNumber;
    @FXML private DatePicker datePurchaseDate;
    @FXML private DatePicker dateExpiryDate;
    @FXML private TextField txtQty;
    @FXML private TextField txtCost;
    @FXML private TextField txtSellingPrice;

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

    private Medicine selectedMedicine;
    private boolean dirty = false;
    private boolean suppressDirtyTracking = false;

    @FXML
    public void initialize() {
        setupCartTable();
        setupHistoryTable();
        setupSupplierCombo();
        setupMedicineAutocomplete();
        setupDirtyTracking();
        syncMedicineMode();
        resetBatchEntryFields();
        loadHistory();
    }

    private void setupCartTable() {
        colMedicine.setCellValueFactory(d -> d.getValue().medicineNameProperty());
        colCompany.setCellValueFactory(d -> d.getValue().companyProperty());
        colBatch.setCellValueFactory(d -> d.getValue().batchNumberProperty());
        colExpiry.setCellValueFactory(d -> d.getValue().expiryDateProperty());
        colQty.setCellValueFactory(d -> d.getValue().receivedQtyProperty());
        colCost.setCellValueFactory(d -> d.getValue().unitCostProperty());
        colSell.setCellValueFactory(d -> d.getValue().sellingPriceProperty());
        colTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getTotalCost()));
        configureCurrencyColumn(colCost);
        configureCurrencyColumn(colSell);
        configureCurrencyColumn(colTotal);
        poTable.setItems(cartItems);
    }

    private void setupHistoryTable() {
        histColId.setCellValueFactory(d -> d.getValue().poIdProperty());
        histColDate.setCellValueFactory(d -> d.getValue().orderDateProperty());
        histColSupplier.setCellValueFactory(d -> d.getValue().supplierNameProperty());
        histColAmount.setCellValueFactory(d -> d.getValue().totalAmountProperty());
        histColStatus.setCellValueFactory(d -> d.getValue().statusProperty());
        configureCurrencyColumn(histColAmount);
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
            comboSupplier.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Supplier item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.getName());
                }
            });
            comboSupplier.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Supplier item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.getName());
                }
            });
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to load active suppliers.", e);
            showAlert(Alert.AlertType.ERROR, "Suppliers", "Failed to load suppliers.");
        }
    }

    private void setupMedicineAutocomplete() {
        listMedicineSuggestions.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Medicine item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String generic = item.getGenericName() == null || item.getGenericName().isBlank()
                        ? ""
                        : " | " + item.getGenericName();
                setText(item.getName() + " (" + item.getCompany() + ")" + generic
                        + " | Stock " + item.getStock());
            }
        });

        txtSearchMedicine.textProperty().addListener((observable, oldValue, newValue) -> {
            if (selectedMedicine != null && !selectedMedicine.getName().equalsIgnoreCase(safe(newValue))) {
                selectedMedicine = null;
                updateSelectedMedicineContext();
            }

            if (chkNewMedicine.isSelected()) {
                hideMedicineSuggestions();
                return;
            }

            if (newValue == null || newValue.trim().length() < 2) {
                hideMedicineSuggestions();
                return;
            }

            AppExecutors.runBackground(() -> {
                try {
                    List<Medicine> results = medicineDAO.searchMedicines(newValue.trim(), 0, 15);
                    Platform.runLater(() -> {
                        listMedicineSuggestions.setItems(FXCollections.observableArrayList(results));
                        listMedicineSuggestions.setVisible(!results.isEmpty());
                        listMedicineSuggestions.setManaged(!results.isEmpty());
                        listMedicineSuggestions.setPrefHeight(Math.min(180, Math.max(1, results.size()) * 28));
                    });
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to search medicines for purchases.", e);
                }
            });
        });

        listMedicineSuggestions.setOnMouseClicked(event -> applySelectedMedicine(
                listMedicineSuggestions.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleMedicineModeChanged() {
        boolean targetNewMedicine = chkNewMedicine.isSelected();
        if (!suppressDirtyTracking && dirty && !confirmDiscardDraft("switch medicine mode")) {
            suppressDirtyTracking = true;
            chkNewMedicine.setSelected(!targetNewMedicine);
            suppressDirtyTracking = false;
            return;
        }
        syncMedicineMode();
    }

    private void syncMedicineMode() {
        boolean newMedicine = chkNewMedicine != null && chkNewMedicine.isSelected();
        existingMedicineBox.setVisible(!newMedicine);
        existingMedicineBox.setManaged(!newMedicine);
        newMedicineBox.setVisible(newMedicine);
        newMedicineBox.setManaged(newMedicine);

        if (newMedicine) {
            selectedMedicine = null;
            txtSearchMedicine.clear();
            hideMedicineSuggestions();
            if (txtNewReorderThreshold != null && safe(txtNewReorderThreshold.getText()).isBlank()) {
                txtNewReorderThreshold.setText("10");
            }
        }
        updateSelectedMedicineContext();
    }

    private void applySelectedMedicine(Medicine medicine) {
        if (medicine == null) {
            return;
        }

        suppressDirtyTracking = true;
        selectedMedicine = medicine;
        txtSearchMedicine.setText(medicine.getName());
        hideMedicineSuggestions();

        if ((txtCost.getText() == null || txtCost.getText().isBlank()) && medicine.getPurchasePrice() > 0) {
            txtCost.setText(String.format(Locale.ROOT, "%.2f", medicine.getPurchasePrice()));
        } else if (txtCost.getText() == null || txtCost.getText().isBlank()) {
            double fallbackCost = medicine.getPrice() > 0 ? medicine.getPrice() * 0.7 : 0.0;
            txtCost.setText(String.format(Locale.ROOT, "%.2f", fallbackCost));
        }

        if (txtSellingPrice.getText() == null || txtSellingPrice.getText().isBlank()) {
            txtSellingPrice.setText(String.format(Locale.ROOT, "%.2f", medicine.getPrice()));
        }

        if ((dateExpiryDate.getValue() == null) && medicine.getExpiry() != null && !medicine.getExpiry().isBlank()) {
            try {
                dateExpiryDate.setValue(LocalDate.parse(medicine.getExpiry()));
            } catch (Exception ignored) {
                // Keep manual entry blank if stored date is malformed.
            }
        }

        updateSelectedMedicineContext();
        suppressDirtyTracking = false;
        txtBatchNumber.requestFocus();
    }

    @FXML
    private void handleAddItem() {
        try {
            PurchaseOrderItem item = buildItemFromForm();
            if (item.getSellingPrice() < item.getUnitCost() && !requireSupervisorApproval(
                    "Below-Cost Purchase Snapshot",
                    "Saving a purchase line with a selling price below cost requires supervisor approval.",
                    "PURCHASE_PRICE_BELOW_COST",
                    item.getMedicineId() > 0 ? item.getMedicineId() : null,
                    Set.of(UserRole.ADMIN, UserRole.MANAGER))) {
                return;
            }
            mergeItemIntoCart(item);
            updateTotal();
            resetBatchEntryFields();
            dirty = !cartItems.isEmpty();

            if (!chkNewMedicine.isSelected()) {
                txtBatchNumber.requestFocus();
            } else {
                txtNewMedicineName.requestFocus();
            }
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.WARNING, "Item Validation", e.getMessage());
        }
    }

    @FXML
    private void handleRemoveItem() {
        PurchaseOrderItem selected = poTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an item to remove.");
            return;
        }
        cartItems.remove(selected);
        updateTotal();
        dirty = dirty || !cartItems.isEmpty();
    }

    @FXML
    private void handleReceiveOrder() {
        Supplier supplier = comboSupplier.getValue();
        if (supplier == null) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a supplier before receiving stock.");
            return;
        }
        if (cartItems.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Cannot receive an empty order.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Receive this order, create any new medicines, and update inventory stock?",
                ButtonType.YES,
                ButtonType.NO);
        confirm.setHeaderText("Confirm stock inward");
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.YES) {
                return;
            }

            try {
                double total = cartItems.stream().mapToDouble(PurchaseOrderItem::getTotalCost).sum();

                PurchaseOrder po = new PurchaseOrder();
                po.setSupplierId(supplier.getSupplierId());
                po.setTotalAmount(total);
                po.setNotes(txtNotes.getText());
                po.setCreatedByUserId(UserSession.getInstance().getUser() != null
                        ? UserSession.getInstance().getUser().getId()
                        : 0);

                poDAO.receivePurchaseOrder(po, List.copyOf(cartItems));

                showAlert(Alert.AlertType.INFORMATION, "Success",
                        "Purchase order received, inventory updated, and batch details recorded.");

                cartItems.clear();
                comboSupplier.setValue(null);
                txtNotes.clear();
                resetEntryState();
                updateTotal();
                loadHistory();
                dirty = false;
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to receive purchase order.", e);
                showAlert(Alert.AlertType.ERROR, "Database Error",
                        "Failed to save purchase order: " + e.getMessage());
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
            LOGGER.log(Level.WARNING, "Failed to load purchase history.", e);
            showAlert(Alert.AlertType.ERROR, "Purchase History", "Failed to load purchase history.");
        }
    }

    private void showPurchaseOrderDetails(PurchaseOrder po) {
        if (po == null) {
            return;
        }

        try {
            List<PurchaseOrderItem> items = poDAO.getPurchaseOrderItems(po.getPoId());

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("PO Details - #" + po.getPoId());
            dialog.setHeaderText("Supplier: " + po.getSupplierName()
                    + "\nDate: " + po.getOrderDate()
                    + "\nStatus: " + po.getStatus()
                    + "\nAmount: ₹ " + String.format(Locale.ROOT, "%.2f", po.getTotalAmount()));

            DialogPane pane = dialog.getDialogPane();
            pane.getButtonTypes().add(ButtonType.CLOSE);
            pane.setPrefWidth(980);
            pane.setPrefHeight(520);

            TableView<PurchaseOrderItem> table = new TableView<>();
            table.setItems(FXCollections.observableArrayList(items));

            TableColumn<PurchaseOrderItem, String> colMed = new TableColumn<>("Medicine");
            colMed.setCellValueFactory(d -> d.getValue().medicineNameProperty());

            TableColumn<PurchaseOrderItem, String> colCmp = new TableColumn<>("Company");
            colCmp.setCellValueFactory(d -> d.getValue().companyProperty());

            TableColumn<PurchaseOrderItem, String> colBatchNo = new TableColumn<>("Batch");
            colBatchNo.setCellValueFactory(d -> d.getValue().batchNumberProperty());

            TableColumn<PurchaseOrderItem, String> colPurchase = new TableColumn<>("Buying Date");
            colPurchase.setCellValueFactory(d -> d.getValue().purchaseDateProperty());

            TableColumn<PurchaseOrderItem, String> colExp = new TableColumn<>("Expiry");
            colExp.setCellValueFactory(d -> d.getValue().expiryDateProperty());

            TableColumn<PurchaseOrderItem, Number> colQ = new TableColumn<>("Qty");
            colQ.setCellValueFactory(d -> d.getValue().receivedQtyProperty());

            TableColumn<PurchaseOrderItem, Number> colC = new TableColumn<>("Cost");
            colC.setCellValueFactory(d -> d.getValue().unitCostProperty());

            TableColumn<PurchaseOrderItem, Number> colS = new TableColumn<>("Sell Price");
            colS.setCellValueFactory(d -> d.getValue().sellingPriceProperty());

            TableColumn<PurchaseOrderItem, Number> colT = new TableColumn<>("Total");
            colT.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getTotalCost()));

            configureCurrencyColumn(colC);
            configureCurrencyColumn(colS);
            configureCurrencyColumn(colT);
            table.getColumns().setAll(List.of(colMed, colCmp, colBatchNo, colPurchase, colExp, colQ, colC, colS, colT));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

            pane.setContent(table);
            dialog.showAndWait();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to load purchase order details.", e);
            showAlert(Alert.AlertType.ERROR, "Purchase Details", "Failed to load purchase order details.");
        }
    }

    private PurchaseOrderItem buildItemFromForm() {
        boolean newMedicine = chkNewMedicine.isSelected();
        LocalDate buyingDate = requireDate(datePurchaseDate, "Buying date is required.");
        LocalDate expiryDate = requireDate(dateExpiryDate, "Expiry date is required.");
        if (expiryDate.isBefore(buyingDate)) {
            throw new IllegalArgumentException("Expiry date cannot be earlier than the buying date.");
        }
        if (ChronoUnit.DAYS.between(LocalDate.now(), expiryDate) < 0) {
            throw new IllegalArgumentException("Expired stock cannot be received into active inventory.");
        }

        int qty = parsePositiveInt(txtQty, "Received quantity must be a whole number greater than zero.");
        double unitCost = parseNonNegativeDouble(txtCost, "Unit cost must be a valid number.");
        double sellingPrice = parsePositiveDouble(txtSellingPrice, "Selling price must be a valid number greater than zero.");
        String batchNumber = safe(txtBatchNumber.getText()).toUpperCase(Locale.ROOT);
        if (batchNumber.isBlank()) {
            throw new IllegalArgumentException("Batch / lot number is required.");
        }

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setBatchNumber(batchNumber);
        item.setPurchaseDate(buyingDate.toString());
        item.setExpiryDate(expiryDate.toString());
        item.setOrderedQty(qty);
        item.setReceivedQty(qty);
        item.setUnitCost(unitCost);
        item.setSellingPrice(sellingPrice);

        if (newMedicine) {
            String medicineName = safe(txtNewMedicineName.getText());
            String company = safe(txtNewCompany.getText());
            if (medicineName.isBlank()) {
                throw new IllegalArgumentException("Medicine name is required for a new medicine.");
            }
            if (company.isBlank()) {
                throw new IllegalArgumentException("Company / manufacturer is required for a new medicine.");
            }
            item.setMedicineId(0);
            item.setMedicineName(medicineName);
            item.setGenericName(safe(txtNewGenericName.getText()));
            item.setCompany(company);
            item.setReorderThreshold(parseOptionalPositiveInt(txtNewReorderThreshold, 10,
                    "Reorder level must be a whole number greater than zero."));
        } else {
            if (selectedMedicine == null) {
                throw new IllegalArgumentException("Please search and select an existing medicine.");
            }
            item.setMedicineId(selectedMedicine.getId());
            item.setMedicineName(selectedMedicine.getName());
            item.setGenericName(safe(selectedMedicine.getGenericName()));
            item.setCompany(safe(selectedMedicine.getCompany()));
            item.setReorderThreshold(10);
        }

        return item;
    }

    private void mergeItemIntoCart(PurchaseOrderItem incoming) {
        for (PurchaseOrderItem existing : cartItems) {
            if (sameCartIdentity(existing, incoming)) {
                int mergedQty = existing.getReceivedQty() + incoming.getReceivedQty();
                existing.setReceivedQty(mergedQty);
                existing.setOrderedQty(mergedQty);
                poTable.refresh();
                return;
            }
        }
        cartItems.add(incoming);
    }

    private boolean sameCartIdentity(PurchaseOrderItem left, PurchaseOrderItem right) {
        String leftMedicineKey = left.getMedicineId() > 0
                ? "id:" + left.getMedicineId()
                : safe(left.getMedicineName()).toLowerCase(Locale.ROOT) + "|" + safe(left.getCompany()).toLowerCase(Locale.ROOT);
        String rightMedicineKey = right.getMedicineId() > 0
                ? "id:" + right.getMedicineId()
                : safe(right.getMedicineName()).toLowerCase(Locale.ROOT) + "|" + safe(right.getCompany()).toLowerCase(Locale.ROOT);

        return leftMedicineKey.equals(rightMedicineKey)
                && safe(left.getBatchNumber()).equalsIgnoreCase(safe(right.getBatchNumber()))
                && safe(left.getExpiryDate()).equals(safe(right.getExpiryDate()))
                && safe(left.getPurchaseDate()).equals(safe(right.getPurchaseDate()))
                && Double.compare(left.getUnitCost(), right.getUnitCost()) == 0
                && Double.compare(left.getSellingPrice(), right.getSellingPrice()) == 0;
    }

    private void updateTotal() {
        double total = cartItems.stream().mapToDouble(PurchaseOrderItem::getTotalCost).sum();
        lblTotalAmount.setText(String.format(Locale.ROOT, "₹ %.2f", total));
        btnReceiveOrder.setDisable(cartItems.isEmpty());
    }

    private void updateSelectedMedicineContext() {
        if (lblMedicineContext == null) {
            return;
        }

        if (selectedMedicine == null) {
            lblMedicineContext.setText(DEFAULT_EXISTING_HINT);
            return;
        }

        String expiry = safe(selectedMedicine.getExpiry()).isBlank() ? "Not set" : selectedMedicine.getExpiry();
        lblMedicineContext.setText("Selected: " + selectedMedicine.getName()
                + " | Company: " + safe(selectedMedicine.getCompany())
                + " | Stock: " + selectedMedicine.getStock()
                + " | Current sell price: ₹ " + String.format(Locale.ROOT, "%.2f", selectedMedicine.getPrice())
                + " | Master expiry snapshot: " + expiry);
    }

    private void resetBatchEntryFields() {
        suppressDirtyTracking = true;
        txtBatchNumber.clear();
        txtQty.clear();
        txtCost.clear();
        txtSellingPrice.clear();
        datePurchaseDate.setValue(LocalDate.now());
        dateExpiryDate.setValue(null);

        if (chkNewMedicine.isSelected()) {
            txtNewMedicineName.clear();
            txtNewGenericName.clear();
            txtNewCompany.clear();
            txtNewReorderThreshold.setText("10");
        } else if (selectedMedicine != null) {
            txtSellingPrice.setText(String.format(Locale.ROOT, "%.2f", selectedMedicine.getPrice()));
            if (selectedMedicine.getPurchasePrice() > 0) {
                txtCost.setText(String.format(Locale.ROOT, "%.2f", selectedMedicine.getPurchasePrice()));
            }
        }
        suppressDirtyTracking = false;
    }

    private void resetEntryState() {
        suppressDirtyTracking = true;
        chkNewMedicine.setSelected(false);
        selectedMedicine = null;
        txtSearchMedicine.clear();
        hideMedicineSuggestions();
        syncMedicineMode();
        resetBatchEntryFields();
        suppressDirtyTracking = false;
    }

    private void hideMedicineSuggestions() {
        listMedicineSuggestions.getItems().clear();
        listMedicineSuggestions.setVisible(false);
        listMedicineSuggestions.setManaged(false);
    }

    private <S> void configureCurrencyColumn(TableColumn<S, Number> column) {
        column.setCellFactory(col -> new TableCell<S, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(Locale.ROOT, "₹ %.2f", item.doubleValue()));
            }
        });
    }

    private int parsePositiveInt(TextField field, String message) {
        try {
            int value = Integer.parseInt(safe(field.getText()));
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private int parseOptionalPositiveInt(TextField field, int defaultValue, String message) {
        if (field == null || safe(field.getText()).isBlank()) {
            return defaultValue;
        }
        return parsePositiveInt(field, message);
    }

    private double parseNonNegativeDouble(TextField field, String message) {
        try {
            double value = Double.parseDouble(safe(field.getText()));
            if (value < 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private double parsePositiveDouble(TextField field, String message) {
        try {
            double value = Double.parseDouble(safe(field.getText()));
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private LocalDate requireDate(DatePicker picker, String message) {
        if (picker.getValue() == null) {
            throw new IllegalArgumentException(message);
        }
        return picker.getValue();
    }

    private void setupDirtyTracking() {
        attachDirtyListener(txtNotes);
        attachDirtyListener(txtNewMedicineName);
        attachDirtyListener(txtNewGenericName);
        attachDirtyListener(txtNewCompany);
        attachDirtyListener(txtNewReorderThreshold);
        attachDirtyListener(txtBatchNumber);
        attachDirtyListener(txtQty);
        attachDirtyListener(txtCost);
        attachDirtyListener(txtSellingPrice);
        if (comboSupplier != null) {
            comboSupplier.valueProperty().addListener((obs, oldVal, newVal) -> markDirty());
        }
        if (datePurchaseDate != null) {
            datePurchaseDate.valueProperty().addListener((obs, oldVal, newVal) -> markDirty());
        }
        if (dateExpiryDate != null) {
            dateExpiryDate.valueProperty().addListener((obs, oldVal, newVal) -> markDirty());
        }
    }

    private void attachDirtyListener(TextInputControl field) {
        if (field == null) {
            return;
        }
        field.textProperty().addListener((obs, oldVal, newVal) -> markDirty());
    }

    private void markDirty() {
        if (!suppressDirtyTracking) {
            dirty = true;
        }
    }

    private boolean requireSupervisorApproval(
            String title,
            String description,
            String actionType,
            Integer entityId,
            Set<UserRole> allowedApproverRoles) {
        var result = SupervisorApprovalDialogs.requestApproval(
                title,
                description,
                actionType,
                "PURCHASE_ORDER_ITEM",
                entityId,
                allowedApproverRoles);
        if (!result.approved()) {
            if (result.message() != null && !"Approval cancelled.".equals(result.message())) {
                showAlert(Alert.AlertType.WARNING, "Approval Needed", result.message());
            }
            return false;
        }
        return true;
    }

    private boolean confirmDiscardDraft(String action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Purchase Draft");
        alert.setHeaderText("You have a purchase draft in progress.");
        alert.setContentText("Discard the current draft and " + action + "?");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public boolean canNavigateAway() {
        return !dirty && cartItems.isEmpty() || confirmDiscardDraft("leave this screen");
    }
}
