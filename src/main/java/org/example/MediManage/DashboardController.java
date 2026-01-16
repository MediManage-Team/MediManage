package org.example.MediManage;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.ArrayList;
import java.util.List;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.BillItem;

import java.io.File;
import java.sql.SQLException;

import java.sql.SQLException;
import org.example.MediManage.service.ReportService;
import net.sf.jasperreports.engine.JRException;
import java.io.IOException;
import javafx.print.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;

public class DashboardController {

    private final ReportService reportService = new ReportService();

    // KPI Labels
    @FXML
    private Label dailySales;
    @FXML
    private Label totalProfit;
    @FXML
    private Label pendingRx;
    @FXML
    private Label lowStock;

    // Inventory Table
    @FXML
    private TableView<Medicine> inventoryTable;
    @FXML
    private TableColumn<Medicine, String> colMedicine;
    @FXML
    private TableColumn<Medicine, String> colCompany; // New
    @FXML
    private TableColumn<Medicine, String> colExpiry;
    @FXML
    private TableColumn<Medicine, Integer> colStock;
    @FXML
    private TableColumn<Medicine, Double> colPrice; // New

    @FXML
    private TextField searchMedicine;

    // Billing Table
    @FXML
    private TableView<BillItem> billingTable;
    @FXML
    private TableColumn<BillItem, String> billColName;
    @FXML
    private TableColumn<BillItem, Integer> billColQty;
    @FXML
    private TableColumn<BillItem, Double> billColTotal;
    @FXML
    private Label lblTotal;

    @FXML
    private TextField txtSearchCustomer;
    @FXML
    private Button btnSearchCustomer;
    @FXML
    private Label lblCustomerStatus;
    @FXML
    private Button btnNewCustomer;
    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab tabAddCustomer;

    @FXML
    private TextField txtDoctorName;

    @FXML
    private Button btnAddBill, btnGenerateInvoice;
    @FXML
    private Button btnUploadPhoto, btnSaveCustomer;

    // Customer Form Fields
    @FXML
    private TextField txtName, txtPhone, txtEmail, txtAddress;
    @FXML
    private TextField txtNomineeName, txtNomineeRelation;
    @FXML
    private TextField txtInsuranceProvider, txtPolicyNo;
    @FXML
    private CheckBox checkDiabetes, checkBP, checkAsthma, checkAllergy;
    @FXML
    private Label lblPhotoPath;

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

    // Data
    private ObservableList<Medicine> masterInventoryList = FXCollections.observableArrayList();
    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private ObservableList<BillHistoryDTO> historyList = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;

    // DAOs
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BillDAO billDAO = new BillDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    @FXML
    private void initialize() {
        // Ensure DB is ready
        try {
            DBUtil.initDB();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Initialization Error", "Database check failed: " + e.getMessage());
        }

        setupInventoryTable();
        setupBillingTable();
        setupHistoryTable();
        loadInventory();
        loadHistory();
        loadKPIs(); // Refresh KPIs on load
        setupBillingButtons();

        // 1. Auto-Focus for Scanner
        javafx.application.Platform.runLater(() -> searchMedicine.requestFocus());

        // 2. Enter Key Listener for Scanner
        searchMedicine.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                // Select first item if available
                if (!inventoryTable.getItems().isEmpty()) {
                    inventoryTable.getSelectionModel().selectFirst();
                    addToBill();
                    // Clear and Refocus for next scan
                    searchMedicine.clear();
                    searchMedicine.requestFocus();
                }
            }
        });
    }

    private void setupHistoryTable() {
        histColId.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getBillId()).asObject());
        histColDate.setCellValueFactory(data -> data.getValue().dateProperty());
        histColCustomer.setCellValueFactory(data -> data.getValue().customerNameProperty());
        histColPhone.setCellValueFactory(data -> data.getValue().phoneProperty());
        histColAmount.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        historyTable.setItems(historyList);

        // History Details Popup
        historyTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !historyTable.getSelectionModel().isEmpty()) {
                showBillDetails(historyTable.getSelectionModel().getSelectedItem());
            }
        });
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

        TableColumn<BillItem, Double> colPrice = new TableColumn<>("Price");
        colPrice.setCellValueFactory(d -> d.getValue().priceProperty().asObject());

        TableColumn<BillItem, Double> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(d -> d.getValue().totalProperty().asObject());

        table.getColumns().addAll(colName, colExpiry, colQty, colPrice, colTotal);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(300);

        // Buttons
        Button btnDownload = new Button("Download Invoice");
        btnDownload.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
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

        Button btnShare = new Button("Share");
        btnShare.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        btnShare.setOnAction(e -> {
            TextInputDialog shareDialog = new TextInputDialog();
            shareDialog.setTitle("Share Invoice");
            shareDialog.setHeaderText("Share Invoice #" + bill.getBillId());
            shareDialog.setContentText("Enter Email / WhatsApp:");
            shareDialog.showAndWait().ifPresent(contact -> {
                showAlert(Alert.AlertType.INFORMATION, "Shared", "Invoice link sent to " + contact + " (Simulated)");
            });
        });

        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10, btnDownload, btnShare);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new javafx.geometry.Insets(10, 0, 0, 0));

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10, table, buttonBox);
        pane.setContent(root);
        dialog.showAndWait();
    }

    private void loadHistory() {
        new Thread(() -> {
            var history = billDAO.getBillHistory();
            javafx.application.Platform.runLater(() -> historyList.setAll(history));
        }).start();
    }

    private void setupInventoryTable() {
        colMedicine.setCellValueFactory(data -> data.getValue().nameProperty());
        colCompany.setCellValueFactory(data -> data.getValue().companyProperty());
        colExpiry.setCellValueFactory(data -> data.getValue().expiryProperty());
        colStock.setCellValueFactory(data -> data.getValue().stockProperty().asObject());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());

        // SEARCH FUNCTIONALITY
        FilteredList<Medicine> filteredData = new FilteredList<>(masterInventoryList, p -> true);

        searchMedicine.textProperty().addListener((observable, oldValue, newValue) -> {
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

        SortedList<Medicine> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(inventoryTable.comparatorProperty());
        inventoryTable.setItems(sortedData);
    }

    private void setupBillingTable() {
        billColName.setCellValueFactory(data -> data.getValue().nameProperty());
        billColQty.setCellValueFactory(data -> data.getValue().qtyProperty().asObject());
        billColTotal.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        billingTable.setItems(billList);
    }

    private void loadKPIs() {
        new Thread(() -> {
            double sales = billDAO.getDailySales();
            long lowStockCount = masterInventoryList.stream().filter(m -> m.getStock() < 10).count();

            javafx.application.Platform.runLater(() -> {
                dailySales.setText(String.format("₹%.2f", sales));
                if (lowStock != null)
                    lowStock.setText(String.valueOf(lowStockCount));
                if (totalProfit != null)
                    totalProfit.setText("₹" + (sales * 0.2));
                if (pendingRx != null)
                    pendingRx.setText("0");
            });
        }).start();
    }

    private void loadInventory() {
        new Thread(() -> {
            var inventory = medicineDAO.getAllMedicines();
            javafx.application.Platform.runLater(() -> {
                masterInventoryList.setAll(inventory);
                // Re-trigger KPI update that depends on inventory (low stock)
                loadKPIs();
            });
        }).start();
    }

    private void setupBillingButtons() {
        btnAddBill.setOnAction(e -> addToBill());
        btnGenerateInvoice.setOnAction(e -> generateInvoice());
    }

    private void addToBill() {
        Medicine selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            // Scanner might trigger this if filter is empty, silent return or beep
            return;
        }

        if (selected.getStock() <= 0) {
            showAlert(Alert.AlertType.ERROR, "Out of Stock", "This medicine is out of stock.");
            return;
        }

        double gstRate = 0.18;
        double gstAmount = selected.getPrice() * gstRate;

        // Check if already in bill
        java.util.Optional<BillItem> existingItem = billList.stream()
                .filter(item -> item.getMedicineId() == selected.getId())
                .findFirst();

        if (existingItem.isPresent()) {
            // Increment Quantity
            BillItem item = existingItem.get();
            int newQty = item.getQty() + 1;

            // Create new item with updated qty (Immutable-ish approach for TableView
            // refresh)
            // Or just update property if BillItem uses JavaFX properties correctly.
            // Our BillItem uses Properties, so we can update them.
            item.qtyProperty().set(newQty);
            item.totalProperty().set((item.getPrice() * newQty) + item.gstProperty().get()); // Recalculate total logic
                                                                                             // needs care

            // For simplicity in this DTO structure, let's remove and re-add or better, just
            // update the table
            billingTable.refresh();
            updateTotal();

        } else {
            BillItem item = new BillItem(selected.getId(), selected.getName(), selected.getExpiry(), 1,
                    selected.getPrice(), gstAmount);
            billList.add(item);
            updateTotal();
        }
    }

    @FXML
    private void handlePhotoUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Photo ID");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(lblPhotoPath.getScene().getWindow());
        if (selectedFile != null) {
            lblPhotoPath.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleSearchCustomer() {
        String query = txtSearchCustomer.getText();
        if (query == null || query.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Required", "Please enter a name or phone number.");
            return;
        }

        List<Customer> results = customerDAO.searchCustomer(query);
        if (results.isEmpty()) {
            // Auto-redirect to Add Customer
            handleNewCustomer();
            return;
        } else {
            // honest assumption: taking the first match for now, or could show a dialog
            selectedCustomer = results.get(0);
            lblCustomerStatus.setText(
                    "Selected: " + selectedCustomer.getName() + " (" + selectedCustomer.getPhoneNumber() + ")");
            lblCustomerStatus.setStyle("-fx-text-fill: green;");
            btnNewCustomer.setVisible(false);
            btnNewCustomer.setManaged(false);
        }
    }

    @FXML
    private void handleNewCustomer() {
        mainTabPane.getSelectionModel().select(tabAddCustomer);
        // Pre-fill phone if search query looks like a number
        String query = txtSearchCustomer.getText();
        if (query != null && query.matches("\\d+")) {
            txtPhone.setText(query);
        }
        // Reset search state
        txtSearchCustomer.clear();
        btnNewCustomer.setVisible(false);
        btnNewCustomer.setManaged(false);
        lblCustomerStatus.setText("No customer selected");
        lblCustomerStatus.setStyle("-fx-text-fill: #666;");
    }

    @FXML
    private void handleSaveCustomer() {
        // Validation
        if (txtName.getText().isEmpty() || txtPhone.getText().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Name and Phone Number are required.");
            return;
        }

        // Gather Diseases
        List<String> diseasesList = new ArrayList<>();
        if (checkDiabetes.isSelected())
            diseasesList.add("Diabetes");
        if (checkBP.isSelected())
            diseasesList.add("Hypertension (BP)");
        if (checkAsthma.isSelected())
            diseasesList.add("Asthma");
        if (checkAllergy.isSelected())
            diseasesList.add("Allergies");

        String diseases = String.join(",", diseasesList);
        String photoPath = lblPhotoPath.getText().equals("No file selected") ? "" : lblPhotoPath.getText();

        Customer customer = new Customer(
                txtName.getText(),
                txtEmail.getText(),
                txtPhone.getText(),
                txtAddress.getText(),
                txtNomineeName.getText(),
                txtNomineeRelation.getText(),
                txtInsuranceProvider.getText(),
                txtPolicyNo.getText(),
                diseases,
                photoPath);

        try {
            customerDAO.addCustomer(customer);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Customer Saved Successfully!");

            // Auto-select the new customer for billing
            // We search for it to get the ID generated by DB trigger/autoincrement
            List<Customer> saved = customerDAO.searchCustomer(customer.getPhoneNumber());
            if (!saved.isEmpty())
                selectedCustomer = saved.get(0);

            clearCustomerForm();
            mainTabPane.getSelectionModel().select(0); // Switch back to Dashboard (Billing)
            if (selectedCustomer != null) {
                txtSearchCustomer.setText(selectedCustomer.getPhoneNumber());
                lblCustomerStatus.setText("Selected: " + selectedCustomer.getName());
                lblCustomerStatus.setStyle("-fx-text-fill: green;");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save customer: " + e.getMessage());
        }
    }

    private void clearCustomerForm() {
        txtName.clear();
        txtPhone.clear();
        txtEmail.clear();
        txtAddress.clear();
        txtNomineeName.clear();
        txtNomineeRelation.clear();
        txtInsuranceProvider.clear();
        txtPolicyNo.clear();
        checkDiabetes.setSelected(false);
        checkBP.setSelected(false);
        checkAsthma.setSelected(false);
        checkAllergy.setSelected(false);
        lblPhotoPath.setText("No file selected");
    }

    private void generateInvoice() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add items to the bill first.");
            return;
        }

        double totalAmount = billList.stream().mapToDouble(BillItem::getTotal).sum();

        Integer customerId = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
        String customerName = selectedCustomer != null ? selectedCustomer.getName() : "Walk-in";

        // Capture list for printing before clearing
        List<BillItem> billListCopy = new ArrayList<>(billList);

        try {
            int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
            int billId = billDAO.generateInvoice(totalAmount, billList, customerId, userId);
            showAlert(Alert.AlertType.INFORMATION, "Success",
                    "Invoice Generated! ID: " + billId + "\nCustomer: " + customerName);

            billList.clear();
            updateTotal();
            loadInventory();
            loadHistory();
            loadKPIs();

            // Reset customer selection
            selectedCustomer = null;
            lblCustomerStatus.setText("No customer selected");
            lblCustomerStatus.setStyle("-fx-text-fill: #666;");
            txtSearchCustomer.clear();

            // Auto-print receipt (Thermal)
            printReceipt(billId, totalAmount, new ArrayList<>(billListCopy)); // Use copy because we cleared list

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save invoice: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Inventory Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("Inventory_" + java.time.LocalDate.now() + ".xlsx");
        File file = fileChooser.showSaveDialog(inventoryTable.getScene().getWindow());

        if (file != null) {
            try {
                reportService.exportInventoryToExcel(new java.util.ArrayList<>(masterInventoryList),
                        file.getAbsolutePath());
                showAlert(Alert.AlertType.INFORMATION, "Success", "Inventory exported successfully!");
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            }
        }
    }

    @FXML
    private void handlePrintInvoice() {
        // For demonstration, we use the current bill list.
        // In a real app, we might want to reprint history items, but user asked for
        // "Print Last Invoice" logic or similar.
        // For now, let's print what is currently in the bill table, or if empty, warn.

        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data", "Current bill is empty. Add items to print.");
            return;
        }

        double total = billList.stream().mapToDouble(BillItem::getTotal).sum();
        String customerName = selectedCustomer != null ? selectedCustomer.getName() : "Walk-in";

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Invoice PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("Invoice_" + System.currentTimeMillis() + ".pdf");
        File file = fileChooser.showSaveDialog(billingTable.getScene().getWindow());

        if (file != null) {
            try {
                reportService.generateInvoicePDF(new java.util.ArrayList<BillItem>(billList), total, customerName,
                        file.getAbsolutePath());
                showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice saved to PDF!");
            } catch (JRException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Print Failed", e.getMessage());
            }
        }
    }

    private void updateTotal() {
        double total = billList.stream().mapToDouble(BillItem::getTotal).sum();
        lblTotal.setText(String.format("₹%.2f", total));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void printReceipt(int billId, double totalAmount, List<BillItem> items) {
        Printer printer = Printer.getDefaultPrinter();
        if (printer == null) {
            showAlert(Alert.AlertType.WARNING, "No Printer", "No default printer found.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null) {
            // Receipt Layout (58mm ~ 180px)
            VBox root = new VBox(5);
            root.setPrefWidth(180);
            root.setMaxWidth(180);
            root.setAlignment(Pos.TOP_LEFT);
            root.setStyle(
                    "-fx-font-family: 'Monospaced'; -fx-font-size: 10px; -fx-background-color: white; -fx-padding: 5;");

            // Header
            Label storeName = new Label("MEDIMANAGE PHARMACY");
            storeName.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            storeName.setMaxWidth(180);
            storeName.setWrapText(true);
            storeName.setAlignment(Pos.CENTER);

            Label date = new Label("Date: " + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            Label billNo = new Label("Bill #: " + billId);
            Label dash = new Label("--------------------------------");

            root.getChildren().addAll(storeName, date, billNo, dash);

            // Items
            for (BillItem item : items) {
                String itemLine = String.format("%-15s\n%2d x %6.2f = %6.2f",
                        item.getName().length() > 15 ? item.getName().substring(0, 15) : item.getName(),
                        item.getQty(), item.getPrice(), item.getTotal());
                Label lblItem = new Label(itemLine);
                root.getChildren().add(lblItem);
            }

            // Footer
            root.getChildren().add(new Label("--------------------------------"));
            Label total = new Label("TOTAL: ₹" + String.format("%.2f", totalAmount));
            total.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            root.getChildren().addAll(total, new Label("Thank you! Visit Again."));

            // Print
            boolean success = job.printPage(root);
            if (success) {
                job.endJob();
            } else {
                showAlert(Alert.AlertType.ERROR, "Print Error", "Failed to print receipt.");
            }
        }
    }

    // ---------------- Inner Classes (DTOs) ----------------

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