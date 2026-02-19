package org.example.MediManage;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.PrescriptionDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Medicine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.example.MediManage.service.ReportService;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import org.example.MediManage.model.Expense;
import javafx.scene.layout.GridPane;
import javafx.application.Platform;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;

/**
 * Refactored DashboardController — focused on KPIs, inventory overview,
 * bill history, business intelligence (AI), and expenses.
 *
 * Billing and customer management are handled by dedicated
 * BillingController and CustomersController.
 */
public class DashboardController {

    private final ReportService reportService = new ReportService();
    private UserSession userSession;

    // KPI Labels
    @FXML
    private Label dailySales;
    @FXML
    private Label totalProfit;
    @FXML
    private Label pendingRx;
    @FXML
    private Label lowStock;

    // Inventory Table (read-only overview)
    @FXML
    private TableView<Medicine> inventoryTable;
    @FXML
    private TableColumn<Medicine, String> colMedicine;
    @FXML
    private TableColumn<Medicine, String> colCompany;
    @FXML
    private TableColumn<Medicine, String> colExpiry;
    @FXML
    private TableColumn<Medicine, Integer> colStock;
    @FXML
    private TableColumn<Medicine, Double> colPrice;
    @FXML
    private TextField searchMedicine;

    // History Table
    @FXML
    private TableView<BillHistoryDTO> historyTable;
    @FXML
    private TableColumn<BillHistoryDTO, Integer> histColId;
    @FXML
    private TableColumn<BillHistoryDTO, String> histColDate;
    @FXML
    private TableColumn<BillHistoryDTO, String> histColCustomer;
    @FXML
    private TableColumn<BillHistoryDTO, String> histColPhone;
    @FXML
    private TableColumn<BillHistoryDTO, Double> histColAmount;

    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab aiTab;

    // Business Intelligence (AI)
    @FXML
    private TextField substituteInput;
    @FXML
    private TextArea substituteResult;
    @FXML
    private TextArea forecastResult;
    @FXML
    private TextArea expiryResult;

    // Data
    private final ObservableList<Medicine> masterInventoryList = FXCollections.observableArrayList();
    private final ObservableList<BillHistoryDTO> historyList = FXCollections.observableArrayList();

    // DAOs
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BillDAO billDAO = new BillDAO();
    private final PrescriptionDAO prescriptionDAO = new PrescriptionDAO();
    private final org.example.MediManage.dao.ExpenseDAO expenseDAO = new org.example.MediManage.dao.ExpenseDAO();

    private org.example.MediManage.service.ai.InventoryAIService inventoryAIService;

    private void initInventoryAI() {
        this.inventoryAIService = new org.example.MediManage.service.ai.InventoryAIService();
    }

    // ======================== INIT ========================

    @FXML
    private void initialize() {
        userSession = UserSession.getInstance();
        initInventoryAI();

        setupInventoryTable();
        setupHistoryTable();
        loadInventory();
        loadHistory();
        loadKPIs();
        setupExpenseTab();
    }

    // ======================== INVENTORY (READ-ONLY) ========================

    private void setupInventoryTable() {
        colMedicine.setCellValueFactory(data -> data.getValue().nameProperty());
        colCompany.setCellValueFactory(data -> data.getValue().companyProperty());
        colExpiry.setCellValueFactory(data -> data.getValue().expiryProperty());
        colStock.setCellValueFactory(data -> data.getValue().stockProperty().asObject());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());

        // Search filter
        FilteredList<Medicine> filteredData = new FilteredList<>(masterInventoryList, p -> true);

        searchMedicine.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(medicine -> {
                if (newValue == null || newValue.isEmpty())
                    return true;
                String lowerCaseFilter = newValue.toLowerCase();
                return medicine.getName().toLowerCase().contains(lowerCaseFilter) ||
                        medicine.getCompany().toLowerCase().contains(lowerCaseFilter);
            });
        });

        SortedList<Medicine> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(inventoryTable.comparatorProperty());
        inventoryTable.setItems(sortedData);
    }

    private void loadInventory() {
        new Thread(() -> {
            var inventory = medicineDAO.getAllMedicines();
            Platform.runLater(() -> {
                masterInventoryList.setAll(inventory);
                loadKPIs();
                checkExpiryAlerts();
            });
        }).start();
    }

    // ======================== KPIs ========================

    private void loadKPIs() {
        new Thread(() -> {
            double sales = billDAO.getDailySales();
            double expenses = expenseDAO.getMonthlyExpenses();
            long lowStockCount = masterInventoryList.stream().filter(m -> m.getStock() < 10).count();

            int pendingCount = 0;
            try {
                pendingCount = prescriptionDAO.getByStatus("PENDING").size();
            } catch (Exception e) {
                // Prescriptions table may not exist yet — gracefully default to 0
            }

            double grossProfit = sales * 0.2;
            double netProfit = grossProfit - expenses;

            final int finalPendingCount = pendingCount;
            Platform.runLater(() -> {
                dailySales.setText(String.format("₹%.2f", sales));
                if (lowStock != null)
                    lowStock.setText(String.valueOf(lowStockCount));
                if (totalProfit != null)
                    totalProfit.setText(String.format("₹%.2f", netProfit));
                if (pendingRx != null)
                    pendingRx.setText(String.valueOf(finalPendingCount));
            });
        }).start();
    }

    // ======================== EXPIRY ALERTS ========================

    private void checkExpiryAlerts() {
        Tab expiryTab = new Tab("Expiry Alerts");
        expiryTab.setClosable(false);

        TableView<Medicine> expiryTable = new TableView<>();

        TableColumn<Medicine, String> colMed = new TableColumn<>("Medicine");
        colMed.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<Medicine, String> colExp = new TableColumn<>("Expiry Date");
        colExp.setCellValueFactory(data -> data.getValue().expiryProperty());

        expiryTable.getColumns().addAll(colMed, colExp);

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate warningDate = today.plusDays(30);

        List<Medicine> expiring = new ArrayList<>();
        for (Medicine m : masterInventoryList) {
            try {
                String expStr = m.getExpiry();
                if (expStr != null && expStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    java.time.LocalDate exp = java.time.LocalDate.parse(expStr);
                    if (!exp.isAfter(warningDate)) {
                        expiring.add(m);
                    }
                }
            } catch (Exception e) {
                // ignore parse error
            }
        }

        if (!expiring.isEmpty()) {
            expiryTable.setItems(FXCollections.observableArrayList(expiring));
            expiryTab.setStyle("-fx-background-color: #ff6b6b30;");
            expiryTab.setContent(expiryTable);
            mainTabPane.getTabs().add(expiryTab);
        }
    }

    // ======================== HISTORY ========================

    private void setupHistoryTable() {
        histColId.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getBillId()).asObject());
        histColDate.setCellValueFactory(data -> data.getValue().dateProperty());
        histColCustomer.setCellValueFactory(data -> data.getValue().customerNameProperty());
        histColPhone.setCellValueFactory(data -> data.getValue().phoneProperty());
        histColAmount.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        historyTable.setItems(historyList);

        // Double-click for bill details
        historyTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !historyTable.getSelectionModel().isEmpty()) {
                showBillDetails(historyTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void loadHistory() {
        new Thread(() -> {
            var history = billDAO.getBillHistory();
            Platform.runLater(() -> historyList.setAll(history));
        }).start();
    }

    private void showBillDetails(BillHistoryDTO bill) {
        if (bill == null)
            return;

        List<BillItem> items = billDAO.getBillItemsExtended(bill.getBillId());

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Bill Details - #" + bill.getBillId());
        dialog.setHeaderText("Customer: " + bill.customerNameProperty().get() + "\n" +
                "Phone: " + bill.phoneProperty().get() + "\n" +
                "Date: " + bill.dateProperty().get());

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(600);

        TableView<BillItem> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(items));

        TableColumn<BillItem, String> colName = new TableColumn<>("Medicine");
        colName.setCellValueFactory(d -> d.getValue().nameProperty());
        colName.setPrefWidth(150);

        TableColumn<BillItem, String> colExpiry = new TableColumn<>("Expiry");
        colExpiry.setCellValueFactory(d -> d.getValue().expiryProperty());

        TableColumn<BillItem, Integer> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(d -> d.getValue().qtyProperty().asObject());

        TableColumn<BillItem, Double> colPr = new TableColumn<>("Price");
        colPr.setCellValueFactory(d -> d.getValue().priceProperty().asObject());

        TableColumn<BillItem, Double> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(d -> d.getValue().totalProperty().asObject());

        table.getColumns().addAll(colName, colExpiry, colQty, colPr, colTotal);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(300);

        // Download button
        Button btnDownload = new Button("Download Invoice");
        btnDownload.getStyleClass().add("button-primary");
        btnDownload.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Invoice PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName("Invoice_" + bill.getBillId() + ".pdf");
            File file = fileChooser.showSaveDialog(dialog.getOwner());

            if (file != null) {
                try {
                    reportService.generateInvoicePDF(items, bill.totalProperty().get(),
                            bill.customerNameProperty().get(), file.getAbsolutePath());
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice saved.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to save PDF: " + ex.getMessage());
                }
            }
        });

        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10, btnDownload);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new javafx.geometry.Insets(10, 0, 0, 0));

        VBox root = new VBox(10, table, buttonBox);
        pane.setContent(root);
        dialog.showAndWait();
    }

    // ======================== BUSINESS INTELLIGENCE (AI) ========================

    @FXML
    private void handleFindSubstitutes() {
        String brand = substituteInput.getText().trim();
        if (brand.isEmpty()) {
            substituteResult.setText("Please enter a brand name.");
            return;
        }
        substituteResult.setText("Analyzing '" + brand + "' with AI and checking inventory...");
        inventoryAIService.findSubstitutes(brand)
                .thenAccept(result -> Platform.runLater(() -> substituteResult.setText(result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> substituteResult.setText("Error: " + ex.getMessage()));
                    return null;
                });
    }

    @FXML
    private void handleRestockReport() {
        forecastResult.setText("Analyzing sales history (last 30 days) for trends...");
        inventoryAIService.generateRestockReport()
                .thenAccept(result -> Platform.runLater(() -> forecastResult.setText(result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> forecastResult.setText("Error: " + ex.getMessage()));
                    return null;
                });
    }

    @FXML
    private void handleExpiryReport() {
        expiryResult.setText("Scanning inventory for expiring items and generating strategy...");
        inventoryAIService.generateExpiryReport()
                .thenAccept(result -> Platform.runLater(() -> expiryResult.setText(result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> expiryResult.setText("Error: " + ex.getMessage()));
                    return null;
                });
    }

    // ======================== EXPORT ========================

    @FXML
    private void handleExportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Inventory Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("Inventory_" + java.time.LocalDate.now() + ".xlsx");
        File file = fileChooser.showSaveDialog(inventoryTable.getScene().getWindow());

        if (file != null) {
            try {
                reportService.exportInventoryToExcel(new ArrayList<>(masterInventoryList), file.getAbsolutePath());
                showAlert(Alert.AlertType.INFORMATION, "Success", "Inventory exported successfully!");
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            }
        }
    }

    // ======================== EXPENSES ========================

    private void setupExpenseTab() {
        Tab expenseTab = new Tab("Expenses");
        expenseTab.setClosable(false);

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(15));

        Button btnAdd = new Button("Record New Expense");
        btnAdd.setOnAction(e -> handleAddExpense());

        Label lblMonthly = new Label("Monthly Expenses: calculating...");

        content.getChildren().addAll(new Label("Expense Manager"), btnAdd, lblMonthly);

        expenseTab.setContent(content);
        mainTabPane.getTabs().add(expenseTab);
    }

    private void handleAddExpense() {
        Dialog<Expense> dialog = new Dialog<>();
        dialog.setTitle("Add Expense");
        dialog.setHeaderText("Record a new shop expense");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField category = new TextField();
        category.setPromptText("Rent, Salary, etc.");
        TextField amount = new TextField();
        amount.setPromptText("0.00");
        TextField description = new TextField();
        description.setPromptText("Optional details");

        grid.add(new Label("Category:"), 0, 0);
        grid.add(category, 1, 0);
        grid.add(new Label("Amount:"), 0, 1);
        grid.add(amount, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(description, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    return new Expense(0, category.getText(), Double.parseDouble(amount.getText()),
                            java.time.LocalDate.now().toString(), description.getText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        java.util.Optional<Expense> result = dialog.showAndWait();
        result.ifPresent(exp -> {
            try {
                expenseDAO.addExpense(exp.getCategory(), exp.getAmount(), exp.getDate(), exp.getDescription());
                loadKPIs();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Expense added.");
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to save expense.");
            }
        });
    }

    // ======================== UTILS ========================

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ======================== INNER CLASSES ========================

    public static class BillHistoryDTO {
        private final int billId;
        private final StringProperty date;
        private final DoubleProperty total;
        private final StringProperty customerName;
        private final StringProperty phone;
        private final StringProperty username;

        public BillHistoryDTO(int billId, String date, double total, String customerName, String phone,
                String username) {
            this.billId = billId;
            this.date = new SimpleStringProperty(date);
            this.total = new SimpleDoubleProperty(total);
            this.customerName = new SimpleStringProperty(customerName != null ? customerName : "N/A");
            this.phone = new SimpleStringProperty(phone != null ? phone : "N/A");
            this.username = new SimpleStringProperty(username != null ? username : "N/A");
        }

        public int getBillId() {
            return billId;
        }

        public StringProperty dateProperty() {
            return date;
        }

        public DoubleProperty totalProperty() {
            return total;
        }

        public StringProperty customerNameProperty() {
            return customerName;
        }

        public StringProperty phoneProperty() {
            return phone;
        }

        public StringProperty usernameProperty() {
            return username;
        }
    }
}