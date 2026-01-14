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

import java.io.File;
import java.sql.SQLException;

public class DashboardController {

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
    private TextField txtCustomerName; // Keep for now if referenced elsewhere, or remove if unused. Checking usage...
                                       // It was used in generateInvoice. Will update logic.
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
        DBUtil.initDB();

        setupInventoryTable();
        setupBillingTable();
        setupHistoryTable();
        loadInventory();
        loadHistory();
        loadKPIs(); // Refresh KPIs on load
        setupBillingButtons();
    }

    private void setupHistoryTable() {
        histColId.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getBillId()).asObject());
        histColDate.setCellValueFactory(data -> data.getValue().dateProperty());
        histColCustomer.setCellValueFactory(data -> data.getValue().customerNameProperty());
        histColPhone.setCellValueFactory(data -> data.getValue().phoneProperty());
        histColAmount.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        historyTable.setItems(historyList);
    }

    private void loadHistory() {
        historyList.setAll(billDAO.getBillHistory());
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
        double sales = billDAO.getDailySales();
        dailySales.setText(String.format("₹%.2f", sales));

        long lowStockCount = masterInventoryList.stream().filter(m -> m.getStock() < 10).count();
        if (lowStock != null)
            lowStock.setText(String.valueOf(lowStockCount));

        if (totalProfit != null)
            totalProfit.setText("₹" + (sales * 0.2)); // Dummy logic for demo
        if (pendingRx != null)
            pendingRx.setText("0");
    }

    private void loadInventory() {
        masterInventoryList.setAll(medicineDAO.getAllMedicines());
    }

    private void setupBillingButtons() {
        btnAddBill.setOnAction(e -> addToBill());
        btnGenerateInvoice.setOnAction(e -> generateInvoice());
    }

    private void addToBill() {
        Medicine selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a medicine from the inventory.");
            return;
        }

        if (selected.getStock() <= 0) {
            showAlert(Alert.AlertType.ERROR, "Out of Stock", "This medicine is out of stock.");
            return;
        }

        double gstRate = 0.18;
        double gstAmount = selected.getPrice() * gstRate;

        // Check availability in current bill list to prevent over-adding
        // For simplicity, we just add new item or increment. Implementation below just
        // adds new row.
        // Ideally should merge.

        // Check if already in bill
        boolean alreadyInBill = billList.stream().anyMatch(item -> item.getMedicineId() == selected.getId());
        if (alreadyInBill) {
            showAlert(Alert.AlertType.INFORMATION, "Item Added",
                    "Item already in bill. (Quantity update feature pending)");
            return;
        }

        BillItem item = new BillItem(selected.getId(), selected.getName(), 1, selected.getPrice(), gstAmount);
        billList.add(item);
        updateTotal();
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
            selectedCustomer = null;
            lblCustomerStatus.setText("Customer not found.");
            lblCustomerStatus.setStyle("-fx-text-fill: red;");
            btnNewCustomer.setVisible(true);
            btnNewCustomer.setManaged(true);
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
            selectedCustomer = customer; // Ideally fetch back efficiently, but object data is sufficient for now if ID
                                         // is not critical immediately or handled
            // However, we need ID for billing. addCustomer executes insert.
            // We should reload or just let user search again. For better UX, let's search
            // for it.
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

        try {
            int billId = billDAO.generateInvoice(totalAmount, billList, customerId);
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

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save invoice: " + e.getMessage());
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

    // ---------------- Inner Classes (DTOs) ----------------
    public static class Medicine {
        private final int id;
        private final StringProperty name;
        private final StringProperty company; // New Field
        private final StringProperty expiry;
        private final IntegerProperty stock;
        private final double price;

        public Medicine(int id, String name, String company, String expiry, int stock, double price) {
            this.id = id;
            this.name = new SimpleStringProperty(name);
            this.company = new SimpleStringProperty(company);
            this.expiry = new SimpleStringProperty(expiry);
            this.stock = new SimpleIntegerProperty(stock);
            this.price = price;
        }

        public StringProperty nameProperty() {
            return name;
        }

        public StringProperty companyProperty() {
            return company;
        }

        public StringProperty expiryProperty() {
            return expiry;
        }

        public IntegerProperty stockProperty() {
            return stock;
        }

        public DoubleProperty priceProperty() {
            return new SimpleDoubleProperty(price);
        } // Wrap for TableColumn

        public int getId() {
            return id;
        }

        public String getName() {
            return name.get();
        }

        public String getCompany() {
            return company.get();
        } // Getter for search

        public int getStock() {
            return stock.get();
        }

        public double getPrice() {
            return price;
        }
    }

    public static class BillItem {
        private final int medicineId;
        private final StringProperty name;
        private final IntegerProperty qty;
        private final DoubleProperty price;
        private final DoubleProperty gst;
        private final DoubleProperty total;

        public BillItem(int medicineId, String name, int qty, double price, double gst) {
            this.medicineId = medicineId;
            this.name = new SimpleStringProperty(name);
            this.qty = new SimpleIntegerProperty(qty);
            this.price = new SimpleDoubleProperty(price);
            this.gst = new SimpleDoubleProperty(gst);
            this.total = new SimpleDoubleProperty((price * qty) + gst);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public IntegerProperty qtyProperty() {
            return qty;
        }

        public DoubleProperty priceProperty() {
            return price;
        }

        public DoubleProperty gstProperty() {
            return gst;
        }

        public DoubleProperty totalProperty() {
            return total;
        }

        public int getMedicineId() {
            return medicineId;
        }

        public int getQty() {
            return qty.get();
        }

        public double getPrice() {
            return price.get();
        }

        public double getTotal() {
            return total.get();
        }
    }

    public static class BillHistoryDTO {
        private final int billId;
        private final StringProperty date;
        private final DoubleProperty total;
        private final StringProperty customerName;
        private final StringProperty phone;

        public BillHistoryDTO(int billId, String date, double total, String customerName, String phone) {
            this.billId = billId;
            this.date = new SimpleStringProperty(date);
            this.total = new SimpleDoubleProperty(total);
            this.customerName = new SimpleStringProperty(customerName != null ? customerName : "N/A");
            this.phone = new SimpleStringProperty(phone != null ? phone : "N/A");
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
    }
}