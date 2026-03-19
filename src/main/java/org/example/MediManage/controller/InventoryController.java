package org.example.MediManage.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.MediManage.model.InventoryBatch;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.InventoryAdjustment;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.service.BarcodeLabelService;
import org.example.MediManage.service.InventoryService;
import org.example.MediManage.util.NavigationGuard;
import org.example.MediManage.util.SupervisorApprovalDialogs;

import javafx.stage.FileChooser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import javafx.application.Platform;
import org.example.MediManage.util.AppExecutors;
public class InventoryController implements NavigationGuard {


    @FXML
    private TextField searchField;
    @FXML
    private Label lblPageInfo;
    @FXML
    private Button btnPrevPage;
    @FXML
    private Button btnNextPage;
    @FXML
    private TableView<Medicine> inventoryTable;
    @FXML
    private TableColumn<Medicine, Integer> colId;
    @FXML
    private TableColumn<Medicine, String> colName;
    @FXML
    private TableColumn<Medicine, String> colGeneric;
    @FXML
    private TableColumn<Medicine, String> colCompany;
    @FXML
    private TableColumn<Medicine, String> colExpiry;
    @FXML
    private TableColumn<Medicine, Integer> colStock;
    @FXML
    private TableColumn<Medicine, Double> colPrice;
    @FXML
    private TableColumn<Medicine, String> colBarcode;

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtGenericName;
    @FXML
    private TextField txtCompany;
    @FXML
    private DatePicker dateExpiry;
    @FXML
    private TextField txtPrice;
    @FXML
    private TextField txtStock;
    @FXML
    private TextField txtPurchasePrice;
    @FXML
    private TextField txtReorderThreshold;
    @FXML
    private TextField txtBarcode;
    @FXML
    private Button btnPrintBarcode;

    @FXML
    private Button btnSave;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnAIRestock;
    @FXML
    private Button btnRecordAdjustment;
    @FXML
    private ListView<InventoryAdjustment> listRecentAdjustments;
    @FXML
    private ListView<InventoryBatch> listBatches;
    @FXML
    private Label lblBatchSummary;
    @FXML
    private TextArea txtAIRestock;
    @FXML
    private ProgressIndicator spinnerRestock;

    private final InventoryService inventoryService = new InventoryService();
    private final BarcodeLabelService barcodeLabelService = new BarcodeLabelService();
    private final ObservableList<Medicine> masterData = FXCollections.observableArrayList();
    private Medicine selectedMedicine = null;
    private int currentPage = 0;
    private int totalItems = 0;
    private int pageSize = 50;
    private boolean keyboardShortcutsRegistered = false;
    private boolean dirty = false;
    private boolean suppressDirtyTracking = false;

    @FXML
    public void initialize() {
        pageSize = inventoryService.defaultPageSize();
        setupTable();
        setupSearch();
        setupKeyboardShortcuts();
        setupRecentAdjustmentsList();
        setupBatchList();
        setupDirtyTracking();
        loadData(0);
        loadRecentAdjustments();
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colGeneric != null) {
            colGeneric.setCellValueFactory(new PropertyValueFactory<>("genericName"));
        }
        colCompany.setCellValueFactory(new PropertyValueFactory<>("company"));
        colExpiry.setCellValueFactory(new PropertyValueFactory<>("expiry"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        if (colBarcode != null) {
            colBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        }

        // Row Factory for Low Stock Alert
        inventoryTable.setRowFactory(tv -> {
            TableRow<Medicine> row = new TableRow<>() {
                @Override
                protected void updateItem(Medicine item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("low-stock-row");
                    if (!empty && item != null && item.getStock() < 10) {
                        getStyleClass().add("low-stock-row");
                    } else {
                        getStyleClass().remove("low-stock-row");
                    }
                }
            };

            MenuItem editBarcodeItem = new MenuItem("Edit Barcode");
            editBarcodeItem.setOnAction(event -> openBarcodeEditor(row.getItem()));

            MenuItem autoGenerateBarcodeItem = new MenuItem("Auto Generate Barcode");
            autoGenerateBarcodeItem.setOnAction(event -> autoGenerateBarcode(row.getItem()));

            ContextMenu contextMenu = new ContextMenu(editBarcodeItem, autoGenerateBarcodeItem);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu));
            return row;
        });

        inventoryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            if (oldVal != null && dirty && oldVal.getId() != newVal.getId() && !confirmDiscardChanges("switch selected medicine")) {
                Platform.runLater(() -> inventoryTable.getSelectionModel().select(oldVal));
                return;
            }
            Medicine loaded = inventoryService.loadMedicine(newVal.getId());
            populateForm(loaded != null ? loaded : newVal);
        });
    }


    private void loadData(int page) {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        int safePage = Math.max(0, page);

        AppExecutors.runBackground(() -> {
            int total = inventoryService.countInventory(query);
            int totalPagesCalc = Math.max(1, (int) Math.ceil(total / (double) pageSize));
            int boundedPage = (safePage >= totalPagesCalc) ? totalPagesCalc - 1 : safePage;

            List<Medicine> pageData = inventoryService.loadInventoryPage(query, boundedPage, pageSize);

            Platform.runLater(() -> {
                totalItems = total;
                currentPage = boundedPage;
                masterData.setAll(pageData);
                inventoryTable.setItems(masterData);
                inventoryTable.refresh();

                if (selectedMedicine != null) {
                    Medicine refreshedSelection = masterData.stream()
                            .filter(m -> m.getId() == selectedMedicine.getId())
                            .findFirst()
                            .orElse(null);
                    if (refreshedSelection == null) {
                        handleClear();
                    } else {
                        inventoryTable.getSelectionModel().select(refreshedSelection);
                        populateForm(refreshedSelection);
                    }
                }

                updatePaginationControls();
            });
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadData(0));
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (keyboardShortcutsRegistered || searchField == null || searchField.getScene() == null) {
                return;
            }
            searchField.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalShortcuts);
            keyboardShortcutsRegistered = true;
        });
    }

    private void handleGlobalShortcuts(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.F) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.N) {
            handleClear();
            txtName.requestFocus();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.S) {
            handleSave();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.R) {
            handleAIRestock();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.DELETE
                && selectedMedicine != null
                && !(event.getTarget() instanceof TextInputControl)) {
            handleDelete();
            event.consume();
        }
    }

    private void populateForm(Medicine med) {
        suppressDirtyTracking = true;
        selectedMedicine = med;
        txtName.setText(med.getName());
        if (txtGenericName != null)
            txtGenericName.setText(med.getGenericName());
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
        if (txtPurchasePrice != null)
            txtPurchasePrice.setText(med.getPurchasePrice() > 0 ? String.valueOf(med.getPurchasePrice()) : "");
        if (txtReorderThreshold != null)
            txtReorderThreshold.setText(String.valueOf(med.getReorderThreshold()));
        if (txtBarcode != null)
            txtBarcode.setText(med.getBarcode());

        btnSave.setText("Update Medicine");
        btnDelete.setDisable(false);
        if (btnRecordAdjustment != null) {
            btnRecordAdjustment.setDisable(false);
        }
        if (btnPrintBarcode != null) {
            btnPrintBarcode.setDisable(med.getBarcode() == null || med.getBarcode().isBlank());
        }
        suppressDirtyTracking = false;
        dirty = false;
        loadMedicineBatches(med.getId());
    }

    @FXML
    private void handleSave() {
        String name = txtName.getText();
        String genericName = txtGenericName == null ? "" : txtGenericName.getText();
        String company = txtCompany.getText();
        LocalDate expiryDate = dateExpiry.getValue();
        String priceStr = txtPrice.getText();
        String stockStr = txtStock.getText();
        String barcode = txtBarcode == null ? "" : txtBarcode.getText();

        if (name == null || name.isBlank() || company == null || company.isBlank() || expiryDate == null || priceStr.isEmpty() || stockStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "All fields are required.");
            return;
        }

        try {
            double price = Double.parseDouble(priceStr);
            int stock = Integer.parseInt(stockStr);
            double purchasePrice = 0.0;
            int reorderThreshold = 10;
            if (txtPurchasePrice != null && !txtPurchasePrice.getText().isEmpty())
                purchasePrice = Double.parseDouble(txtPurchasePrice.getText());
            if (txtReorderThreshold != null && !txtReorderThreshold.getText().isEmpty())
                reorderThreshold = Integer.parseInt(txtReorderThreshold.getText());

            if (price <= 0) {
                throw new NumberFormatException("Selling price must be greater than zero.");
            }
            if (stock < 0) {
                throw new NumberFormatException("Stock cannot be negative.");
            }
            if (purchasePrice < 0) {
                throw new NumberFormatException("Cost price cannot be negative.");
            }
            if (reorderThreshold <= 0) {
                throw new NumberFormatException("Reorder threshold must be at least 1.");
            }
            if (expiryDate.isBefore(LocalDate.now())) {
                showAlert(Alert.AlertType.ERROR, "Expired Medicine", "Expiry date cannot be in the past for an active medicine.");
                return;
            }
            if (inventoryService.isBarcodeAssignedToAnotherMedicine(selectedMedicine == null ? 0 : selectedMedicine.getId(), barcode)) {
                showAlert(Alert.AlertType.ERROR, "Duplicate Barcode", "This barcode is already assigned to another medicine.");
                return;
            }
            if (price < purchasePrice && !requireSupervisorApproval(
                    "Low Margin Approval",
                    "Selling price is below cost price. Supervisor approval is required.",
                    "PRICE_BELOW_COST",
                    selectedMedicine == null ? null : selectedMedicine.getId(),
                    Set.of(UserRole.ADMIN, UserRole.MANAGER))) {
                return;
            }
            if (selectedMedicine != null && stock < selectedMedicine.getStock() && !requireSupervisorApproval(
                    "Stock Reduction Approval",
                    "Reducing on-hand stock manually requires supervisor approval.",
                    "MANUAL_STOCK_REDUCTION",
                    selectedMedicine.getId(),
                    Set.of(UserRole.ADMIN))) {
                return;
            }

            if (selectedMedicine == null) {
                inventoryService.addMedicine(name, genericName, company, expiryDate, price, stock, purchasePrice,
                        reorderThreshold, barcode);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Medicine added.");
            } else {
                inventoryService.updateMedicine(selectedMedicine, name, genericName, company, expiryDate, price, stock,
                        purchasePrice, reorderThreshold, barcode);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Medicine updated.");
            }
            handleClear();
            loadData(currentPage);

        } catch (NumberFormatException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Price must be numeric and stock must be a whole number."
                    : e.getMessage();
            showAlert(Alert.AlertType.ERROR, "Error", message);
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedMedicine == null)
            return;

        if (!requireSupervisorApproval(
                "Delete Medicine",
                "Deleting a medicine is a high-risk action and requires supervisor approval.",
                "DELETE_MEDICINE",
                selectedMedicine.getId(),
                Set.of(UserRole.ADMIN))) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Medicine");
        alert.setContentText("Delete " + selectedMedicine.getName() + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            inventoryService.deleteMedicine(selectedMedicine.getId());
            handleClear();
            loadData(currentPage);
        }
    }

    @FXML
    private void handleClear() {
        if (!suppressDirtyTracking && dirty && !confirmDiscardChanges("clear the form")) {
            return;
        }
        suppressDirtyTracking = true;
        selectedMedicine = null;
        txtName.clear();
        if (txtGenericName != null)
            txtGenericName.clear();
        txtCompany.clear();
        dateExpiry.setValue(null);
        txtPrice.clear();
        txtStock.clear();
        if (txtPurchasePrice != null)
            txtPurchasePrice.clear();
        if (txtReorderThreshold != null)
            txtReorderThreshold.clear();
        if (txtBarcode != null)
            txtBarcode.clear();
        inventoryTable.getSelectionModel().clearSelection();
        btnSave.setText("Add New");
        btnDelete.setDisable(true);
        if (btnRecordAdjustment != null) {
            btnRecordAdjustment.setDisable(true);
        }
        if (btnPrintBarcode != null) {
            btnPrintBarcode.setDisable(true);
        }
        if (listBatches != null) {
            listBatches.getItems().clear();
        }
        if (lblBatchSummary != null) {
            lblBatchSummary.setText("Select a medicine to view active batches, nearest expiry, and available units.");
        }
        suppressDirtyTracking = false;
        dirty = false;
    }

    @FXML
    private void handleGenerateBarcode() {
        if (selectedMedicine == null) {
            showAlert(Alert.AlertType.INFORMATION, "Save First",
                    "Save the medicine first, then use Generate Barcode to create an ID-based barcode.");
            return;
        }
        autoGenerateBarcode(selectedMedicine);
    }

    @FXML
    private void handlePrintBarcodeLabel() {
        if (selectedMedicine == null) {
            showAlert(Alert.AlertType.WARNING, "No Medicine Selected", "Select a medicine first.");
            return;
        }
        if (selectedMedicine.getBarcode() == null || selectedMedicine.getBarcode().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "No Barcode", "Generate or assign a barcode first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Barcode Label");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName(selectedMedicine.getName().replaceAll("[^A-Za-z0-9._-]+", "_") + "_label.pdf");
        java.io.File file = fileChooser.showSaveDialog(inventoryTable.getScene().getWindow());
        if (file == null) {
            return;
        }
        AppExecutors.runBackground(() -> {
            try {
                barcodeLabelService.exportMedicineLabelPdf(selectedMedicine, file.getAbsolutePath());
                Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Label Ready",
                        "Barcode label saved to " + file.getName() + "."));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Print Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleExportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Inventory as CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("inventory_export_" + java.time.LocalDate.now() + ".csv");
        java.io.File file = fileChooser.showSaveDialog(inventoryTable.getScene().getWindow());

        if (file != null) {
            AppExecutors.runBackground(() -> {
                try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                    writer.println("ID,Name,Company,Expiry Date,Stock,Price");
                    for (Medicine m : masterData) {
                        writer.printf("%d,\"%s\",\"%s\",%s,%d,%.2f%n",
                                m.getId(),
                                m.getName().replace("\"", "\"\""),
                                m.getCompany().replace("\"", "\"\""),
                                m.getExpiry() != null ? m.getExpiry() : "",
                                m.getStock(),
                                m.getPrice());
                    }
                    Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Inventory exported to " + file.getName()));
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Export Failed", "Error exporting to CSV: " + e.getMessage()));
                }
            });
        }
    }

    @FXML
    private void handleAIRestock() {
        List<Medicine> snapshot = inventoryService.loadRestockAnalysisSnapshot();
        InventoryService.RestockPreparation prep = inventoryService.prepareRestock(snapshot);

        var ctx = org.example.MediManage.util.AIResultDialog.showLoadingPopup(
                "Restock Analysis", "", "Analyzing stock levels and sales trends...");

        if (!prep.requiresAi()) {
            ctx.setResult(prep.message());
            return;
        }

        inventoryService.suggestRestock(prep.snapshot())
                .thenAccept(result -> ctx.setResult(result))
                .exceptionally(ex -> {
                    // Offline fallback: show the low-stock data directly
                    StringBuilder sb = new StringBuilder();
                    sb.append("Restock Suggestions (Local Analysis)\n\n");
                    sb.append("Low Stock Items:\n");
                    sb.append(prep.snapshot());
                    sb.append("\n\nTip: Configure your Cloud AI API key in Settings for deeper analysis.");
                    ctx.setResult(sb.toString());
                    return null;
                });
    }

    private void setupRecentAdjustmentsList() {
        if (listRecentAdjustments == null) {
            return;
        }
        listRecentAdjustments.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryAdjustment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String typeLabel = "RETURN".equalsIgnoreCase(item.getAdjustmentType()) ? "Return" : "Damaged";
                if ("Damaged".equals(typeLabel) && item.getRootCauseTag() != null) {
                    String normalizedCause = item.getRootCauseTag().trim().toLowerCase(Locale.ROOT);
                    if (normalizedCause.contains("dump") || normalizedCause.contains("waste")) {
                        typeLabel = "Dump";
                    }
                }
                String reason = item.getRootCauseTag() == null || item.getRootCauseTag().isBlank()
                        ? ""
                        : " | " + item.getRootCauseTag();
                String by = item.getCreatedByUsername() == null || item.getCreatedByUsername().isBlank()
                        ? ""
                        : " | " + item.getCreatedByUsername();
                setText(item.getOccurredAt()
                        + " | "
                        + typeLabel
                        + " | "
                        + item.getMedicineName()
                        + " | Qty "
                        + item.getQuantity()
                        + " | ₹ "
                        + String.format(Locale.ROOT, "%.2f", item.getUnitPrice())
                        + reason
                        + by);
            }
        });
    }

    private void setupBatchList() {
        if (listBatches == null) {
            return;
        }
        listBatches.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryBatch item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String expiryInfo = safe(item.expiryDate())
                        + (item.daysToExpiry() == null ? "" : " (" + item.daysToExpiry() + "d)");
                setText("#" + item.expirySequence()
                        + " | " + item.batchNumber()
                        + " | " + safe(item.batchBarcode())
                        + " | Exp " + expiryInfo
                        + " | Qty " + item.availableQuantity()
                        + " | Cost ₹" + String.format(Locale.ROOT, "%.2f", item.unitCost()));
            }
        });
    }

    private void setupDirtyTracking() {
        if (searchField != null) {
            attachDirtyListener(txtName);
            attachDirtyListener(txtGenericName);
            attachDirtyListener(txtCompany);
            attachDirtyListener(txtPrice);
            attachDirtyListener(txtStock);
            attachDirtyListener(txtPurchasePrice);
            attachDirtyListener(txtReorderThreshold);
            attachDirtyListener(txtBarcode);
            if (dateExpiry != null) {
                dateExpiry.valueProperty().addListener((obs, oldVal, newVal) -> markDirty());
            }
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

    private void loadRecentAdjustments() {
        if (listRecentAdjustments == null) {
            return;
        }

        AppExecutors.runBackground(() -> {
            try {
                List<InventoryAdjustment> recent = inventoryService.loadRecentAdjustments(8);
                Platform.runLater(() -> listRecentAdjustments.setItems(FXCollections.observableArrayList(recent)));
            } catch (Exception e) {
                Platform.runLater(() -> listRecentAdjustments.setItems(FXCollections.observableArrayList()));
            }
        });
    }

    private void loadMedicineBatches(int medicineId) {
        if (listBatches == null) {
            return;
        }
        AppExecutors.runBackground(() -> {
            try {
                List<InventoryBatch> batches = inventoryService.loadMedicineBatches(medicineId);
                Platform.runLater(() -> {
                    listBatches.setItems(FXCollections.observableArrayList(batches));
                    if (lblBatchSummary != null) {
                        int totalQty = batches.stream().mapToInt(InventoryBatch::availableQuantity).sum();
                        lblBatchSummary.setText(
                                batches.isEmpty()
                                        ? "No active batches recorded for this medicine yet."
                                        : "Active batches: " + batches.size() + " | Available units: " + totalQty);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    listBatches.setItems(FXCollections.observableArrayList());
                    if (lblBatchSummary != null) {
                        lblBatchSummary.setText("Batch overview unavailable: " + e.getMessage());
                    }
                });
            }
        });
    }

    @FXML
    private void handleRecordAdjustment() {
        if (selectedMedicine == null) {
            showAlert(Alert.AlertType.WARNING, "No Medicine Selected", "Select a medicine before recording a stock adjustment.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record Stock Write-Off");
        dialog.setHeaderText("Record a stock reduction for " + selectedMedicine.getName());

        ButtonType saveButtonType = new ButtonType("Save Adjustment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("DAMAGED", "RETURN", "DUMP"));
        typeCombo.setValue("DAMAGED");

        TextField qtyField = new TextField();
        qtyField.setPromptText("Quantity");

        TextField unitPriceField = new TextField();
        double defaultUnitPrice = selectedMedicine.getPurchasePrice() > 0
                ? selectedMedicine.getPurchasePrice()
                : selectedMedicine.getPrice();
        unitPriceField.setText(String.format(Locale.ROOT, "%.2f", defaultUnitPrice));

        ComboBox<String> rootCauseCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Expired Stock",
                "Breakage",
                "Supplier Return",
                "Packaging Damage",
                "Storage Damage",
                "Quality Issue",
                "Short Expiry"));
        rootCauseCombo.setEditable(true);
        rootCauseCombo.setValue("Expired Stock");
        typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("DUMP".equalsIgnoreCase(newVal)) {
                if (rootCauseCombo.getEditor().getText() == null || rootCauseCombo.getEditor().getText().isBlank()
                        || "Expired Stock".equalsIgnoreCase(rootCauseCombo.getEditor().getText())) {
                    rootCauseCombo.setValue("Expired Dump");
                }
            } else if ("RETURN".equalsIgnoreCase(newVal) && (rootCauseCombo.getEditor().getText() == null
                    || rootCauseCombo.getEditor().getText().isBlank()
                    || "Expired Dump".equalsIgnoreCase(rootCauseCombo.getEditor().getText()))) {
                rootCauseCombo.setValue("Supplier Return");
            }
        });

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Notes / invoice reference / operator comment");
        notesArea.setPrefRowCount(3);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c62828;");
        errorLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Type"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("Qty"), 0, 1);
        grid.add(qtyField, 1, 1);
        grid.add(new Label("Unit Price"), 0, 2);
        grid.add(unitPriceField, 1, 2);
        grid.add(new Label("Root Cause"), 0, 3);
        grid.add(rootCauseCombo, 1, 3);
        grid.add(new Label("Notes"), 0, 4);
        grid.add(notesArea, 1, 4);
        grid.add(errorLabel, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                int qty = Integer.parseInt(qtyField.getText().trim());
                double unitPrice = Double.parseDouble(unitPriceField.getText().trim());
                if (!requireSupervisorApproval(
                        "Stock Write-off Approval",
                        "Returns, expired stock, and damage write-offs require supervisor approval.",
                        "INVENTORY_WRITE_OFF",
                        selectedMedicine.getId(),
                        Set.of(UserRole.ADMIN))) {
                    event.consume();
                    return;
                }
                Integer currentUserId = org.example.MediManage.util.UserSession.getInstance().getUser() != null
                        ? org.example.MediManage.util.UserSession.getInstance().getUser().getId()
                        : null;
                String selectedType = typeCombo.getValue();
                String rootCause = rootCauseCombo.getEditor().getText();
                if ("DUMP".equalsIgnoreCase(selectedType) && (rootCause == null || rootCause.isBlank())) {
                    rootCause = "Expired Dump";
                }

                inventoryService.recordAdjustment(
                        selectedMedicine,
                        selectedType,
                        qty,
                        unitPrice,
                        rootCause,
                        notesArea.getText(),
                        currentUserId);
            } catch (NumberFormatException e) {
                errorLabel.setText("Quantity must be a whole number and unit price must be numeric.");
                event.consume();
            } catch (Exception e) {
                errorLabel.setText(e.getMessage());
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveButtonType) {
            showAlert(Alert.AlertType.INFORMATION, "Adjustment Recorded",
                    "Stock adjustment saved for " + selectedMedicine.getName() + ".");
            loadData(currentPage);
            loadRecentAdjustments();
        }
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 0) {
            loadData(currentPage - 1);
        }
    }

    @FXML
    private void handleNextPage() {
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        if (currentPage + 1 < totalPages) {
            loadData(currentPage + 1);
        }
    }

    private void updatePaginationControls() {
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        int start = totalItems == 0 ? 0 : (currentPage * pageSize) + 1;
        int end = totalItems == 0 ? 0 : Math.min(totalItems, (currentPage + 1) * pageSize);

        if (lblPageInfo != null) {
            lblPageInfo.setText(String.format("Rows %d-%d of %d (Page %d/%d)",
                    start, end, totalItems, currentPage + 1, totalPages));
        }
        if (btnPrevPage != null) {
            btnPrevPage.setDisable(currentPage <= 0);
        }
        if (btnNextPage != null) {
            btnNextPage.setDisable(currentPage + 1 >= totalPages);
        }
    }

    private void openBarcodeEditor(Medicine medicine) {
        if (medicine == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Barcode");
        dialog.setHeaderText("Set barcode for " + medicine.getName());

        ButtonType saveButtonType = new ButtonType("Save Barcode", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField barcodeField = new TextField(medicine.getBarcode());
        barcodeField.setPromptText("Enter barcode");

        Button autoGenerateButton = new Button("Auto Generate");
        autoGenerateButton.setOnAction(event -> barcodeField.setText(inventoryService.generateBarcode(medicine.getId())));

        Label helperLabel = new Label("Use a scanner-compatible code or auto-generate a deterministic MED code.");
        helperLabel.setWrapText(true);
        helperLabel.setStyle("-fx-text-fill: #7a8399; -fx-font-size: 11px;");

        VBox content = new VBox(10, barcodeField, autoGenerateButton, helperLabel);
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                if (inventoryService.isBarcodeAssignedToAnotherMedicine(medicine.getId(), barcodeField.getText())) {
                    throw new IllegalArgumentException("This barcode is already assigned to another medicine.");
                }
                inventoryService.updateBarcode(medicine.getId(), barcodeField.getText());
                medicine.setBarcode(barcodeField.getText());
                if (selectedMedicine != null && selectedMedicine.getId() == medicine.getId() && txtBarcode != null) {
                    txtBarcode.setText(barcodeField.getText());
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Barcode Error", e.getMessage());
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveButtonType) {
            loadData(currentPage);
        }
    }

    private void autoGenerateBarcode(Medicine medicine) {
        if (medicine == null) {
            return;
        }
        String generatedBarcode = inventoryService.generateBarcode(medicine.getId());
        if (inventoryService.isBarcodeAssignedToAnotherMedicine(medicine.getId(), generatedBarcode)) {
            showAlert(Alert.AlertType.ERROR, "Barcode Collision",
                    "Generated barcode " + generatedBarcode + " is already assigned to another medicine.");
            return;
        }
        inventoryService.updateBarcode(medicine.getId(), generatedBarcode);
        medicine.setBarcode(generatedBarcode);
        if (selectedMedicine != null && selectedMedicine.getId() == medicine.getId() && txtBarcode != null) {
            txtBarcode.setText(generatedBarcode);
        }
        showAlert(Alert.AlertType.INFORMATION, "Barcode Updated",
                "Generated barcode " + generatedBarcode + " for " + medicine.getName() + ".");
        loadData(currentPage);
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
                "MEDICINE",
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

    private boolean confirmDiscardChanges(String action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("You have unsaved changes.");
        alert.setContentText("Discard changes and " + action + "?");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    @Override
    public boolean canNavigateAway() {
        return !dirty || confirmDiscardChanges("leave this screen");
    }
}
