package org.example.MediManage;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.MedicineDAO;

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
    private TextField txtCustomerName; // New
    @FXML
    private TextField txtDoctorName; // New

    @FXML
    private Button btnAddBill, btnGenerateInvoice;

    // Data
    private ObservableList<Medicine> masterInventoryList = FXCollections.observableArrayList();
    private ObservableList<BillItem> billList = FXCollections.observableArrayList();

    // DAOs
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BillDAO billDAO = new BillDAO();

    @FXML
    private void initialize() {
        // Ensure DB is ready
        DBUtil.initDB();

        setupInventoryTable();
        setupBillingTable();
        loadInventory();
        loadKPIs(); // Refresh KPIs on load
        setupBillingButtons();
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

    private void generateInvoice() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add items to the bill first.");
            return;
        }

        double totalAmount = billList.stream().mapToDouble(BillItem::getTotal).sum();

        try {
            int billId = billDAO.generateInvoice(totalAmount, billList);
            showAlert(Alert.AlertType.INFORMATION, "Success",
                    "Invoice Generated! ID: " + billId + "\nCustomer: " + txtCustomerName.getText());

            billList.clear();
            txtCustomerName.clear(); // Reset inputs
            txtDoctorName.clear();
            updateTotal();
            loadInventory();
            loadKPIs();
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
}