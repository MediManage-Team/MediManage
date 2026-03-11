package org.example.MediManage.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.service.InventoryService;
import org.example.MediManage.util.AsyncUiFeedback;
import javafx.stage.FileChooser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import org.example.MediManage.util.AppExecutors;
public class InventoryController {
    private static final String RESTOCK_READY_LABEL = "✨ Get AI Suggestions";
    private static final String RESTOCK_BUSY_LABEL = "⏳ Running...";

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
    private TextField txtPurchasePrice;
    @FXML
    private TextField txtReorderThreshold;

    @FXML
    private Button btnSave;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnAIRestock;
    @FXML
    private TextArea txtAIRestock;
    @FXML
    private ProgressIndicator spinnerRestock;

    private final InventoryService inventoryService = new InventoryService();
    private final ObservableList<Medicine> masterData = FXCollections.observableArrayList();
    private Medicine selectedMedicine = null;
    private int currentPage = 0;
    private int totalItems = 0;
    private int pageSize = 50;
    private boolean keyboardShortcutsRegistered = false;

    @FXML
    public void initialize() {
        pageSize = inventoryService.defaultPageSize();
        setupTable();
        setupSearch();
        setupKeyboardShortcuts();
        loadData(0);
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
                getStyleClass().remove("low-stock-row");
                if (!empty && item != null && item.getStock() < 10) {
                    getStyleClass().add("low-stock-row");
                } else {
                    getStyleClass().remove("low-stock-row");
                }
            }
        });

        inventoryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                populateForm(newVal);
            }
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
                    boolean selectedStillVisible = masterData.stream().anyMatch(m -> m.getId() == selectedMedicine.getId());
                    if (!selectedStillVisible) {
                        handleClear();
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
        if (txtPurchasePrice != null)
            txtPurchasePrice.setText(med.getPurchasePrice() > 0 ? String.valueOf(med.getPurchasePrice()) : "");
        if (txtReorderThreshold != null)
            txtReorderThreshold.setText("");

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
            double purchasePrice = 0.0;
            int reorderThreshold = 10;
            if (txtPurchasePrice != null && !txtPurchasePrice.getText().isEmpty())
                purchasePrice = Double.parseDouble(txtPurchasePrice.getText());
            if (txtReorderThreshold != null && !txtReorderThreshold.getText().isEmpty())
                reorderThreshold = Integer.parseInt(txtReorderThreshold.getText());

            if (selectedMedicine == null) {
                inventoryService.addMedicine(name, company, expiryDate, price, stock, purchasePrice, reorderThreshold);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Medicine added.");
            } else {
                inventoryService.updateMedicine(selectedMedicine, name, company, expiryDate, price, stock,
                        purchasePrice, reorderThreshold);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Medicine updated.");
            }
            handleClear();
            loadData(currentPage);

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
            inventoryService.deleteMedicine(selectedMedicine.getId());
            handleClear();
            loadData(currentPage);
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
        if (txtPurchasePrice != null)
            txtPurchasePrice.clear();
        if (txtReorderThreshold != null)
            txtReorderThreshold.clear();
        inventoryTable.getSelectionModel().clearSelection();
        btnSave.setText("Add New");
        btnDelete.setDisable(true);
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
        InventoryService.RestockPreparation restock = inventoryService.prepareRestock(snapshot);
        if (!restock.requiresAi()) {
            AsyncUiFeedback.showSuccess(btnAIRestock, spinnerRestock, txtAIRestock,
                    RESTOCK_READY_LABEL, restock.message());
            return;
        }

        AsyncUiFeedback.showLoading(btnAIRestock, spinnerRestock, txtAIRestock,
                RESTOCK_BUSY_LABEL, "⏳ Running AI restock analysis...");

        inventoryService.suggestRestock(restock.snapshot())
                .thenAccept(result -> Platform.runLater(() -> {
                    AsyncUiFeedback.showSuccess(btnAIRestock, spinnerRestock, txtAIRestock,
                            RESTOCK_READY_LABEL, result);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        AsyncUiFeedback.showError(btnAIRestock, spinnerRestock, txtAIRestock,
                                RESTOCK_READY_LABEL, ex);
                    });
                    return null;
                });
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
