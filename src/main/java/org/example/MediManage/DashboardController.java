package org.example.MediManage;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.*;
import java.time.LocalDate;

public class DashboardController {

    @FXML private Label dailySales;
    @FXML private Label totalProfit;
    @FXML private Label pendingRx;
    @FXML private Label lowStock;

    @FXML private TableView<Medicine> inventoryTable;
    @FXML private TableColumn<Medicine, String> colMedicine;
    @FXML private TableColumn<Medicine, String> colBatch;
    @FXML private TableColumn<Medicine, String> colExpiry;
    @FXML private TableColumn<Medicine, String> colHSN;
    @FXML private TableColumn<Medicine, Integer> colStock;

    @FXML private TextField searchMedicine;
    @FXML private TableView<BillItem> billingTable;
    @FXML private TableColumn<BillItem, String> billColName;
    @FXML private TableColumn<BillItem, Integer> billColQty;
    @FXML private TableColumn<BillItem, Double> billColPrice;
    @FXML private TableColumn<BillItem, Double> billColGST;
    @FXML private TableColumn<BillItem, Double> billColTotal;
    @FXML private Label lblTotal;

    @FXML private Button btnAddBill, btnGenerateInvoice;

    private ObservableList<Medicine> inventoryList = FXCollections.observableArrayList();
    private ObservableList<BillItem> billList = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Ensure DB is ready (optional if called in Launcher)
        DBUtil.initDB();

        setupInventoryTable();
        setupBillingTable();
        loadKPIs();
        loadInventory();
        setupBillingButtons();
    }

    private void setupInventoryTable() {
        colMedicine.setCellValueFactory(data -> data.getValue().nameProperty());
        colBatch.setCellValueFactory(data -> data.getValue().batchProperty());
        colExpiry.setCellValueFactory(data -> data.getValue().expiryProperty());
        colHSN.setCellValueFactory(data -> data.getValue().hsnProperty());
        colStock.setCellValueFactory(data -> data.getValue().stockProperty().asObject());
        inventoryTable.setItems(inventoryList);
    }

    private void setupBillingTable() {
        billColName.setCellValueFactory(data -> data.getValue().nameProperty());
        billColQty.setCellValueFactory(data -> data.getValue().qtyProperty().asObject());
        billColPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());
        billColGST.setCellValueFactory(data -> data.getValue().gstProperty().asObject());
        billColTotal.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        billingTable.setItems(billList);
    }

    private void loadKPIs() {
        try (Connection conn = DBUtil.getConnection()) {
            Statement stmt = conn.createStatement();

            // 1. Daily Sales (MySQL Syntax: CURDATE())
            String salesSql = "SELECT IFNULL(SUM(total_amount), 0) AS daily_sales FROM bills WHERE DATE(bill_date) = CURDATE()";
            ResultSet rs = stmt.executeQuery(salesSql);
            if (rs.next()) dailySales.setText("₹" + rs.getDouble("daily_sales"));

            // 2. Low Stock (Using 'stock' table)
            String stockSql = "SELECT COUNT(*) AS low_stock FROM stock WHERE quantity < 10";
            rs = stmt.executeQuery(stockSql);
            if (rs.next()) lowStock.setText(rs.getString("low_stock"));

            // 3. Pending Rx & Profit (Placeholders as schema doesn't have these tables/columns yet)
            totalProfit.setText("₹0.0");
            pendingRx.setText("0");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadInventory() {
        inventoryList.clear();
        // JOIN medicines and stock tables
        String sql = "SELECT m.medicine_id, m.name, m.company, m.expiry_date, m.price, s.quantity " +
                "FROM medicines m " +
                "JOIN stock s ON m.medicine_id = s.medicine_id";

        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                inventoryList.add(new Medicine(
                        rs.getInt("medicine_id"),
                        rs.getString("name"),
                        "N/A", // Batch not in schema
                        rs.getString("expiry_date"),
                        "N/A", // HSN not in schema
                        rs.getInt("quantity"),
                        rs.getDouble("price")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        // Check if stock is sufficient
        if (selected.getStock() <= 0) {
            showAlert(Alert.AlertType.ERROR, "Out of Stock", "This medicine is out of stock.");
            return;
        }

        // Add to bill list
        // Assuming GST is 18% for now (You can add GST column to DB later)
        double gstRate = 0.18;
        double gstAmount = selected.getPrice() * gstRate;

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
        Connection conn = null;

        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false); // Start Transaction

            // 1. Insert into bills table
            String billSql = "INSERT INTO bills (total_amount, bill_date) VALUES (?, NOW())";
            PreparedStatement psBill = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS);
            psBill.setDouble(1, totalAmount);
            psBill.executeUpdate();

            // Get generated Bill ID
            ResultSet rs = psBill.getGeneratedKeys();
            int billId = 0;
            if (rs.next()) {
                billId = rs.getInt(1);
            }

            // 2. Insert items and Update Stock
            String itemSql = "INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (?, ?, ?, ?, ?)";
            String stockSql = "UPDATE stock SET quantity = quantity - ? WHERE medicine_id = ?";

            PreparedStatement psItem = conn.prepareStatement(itemSql);
            PreparedStatement psStock = conn.prepareStatement(stockSql);

            for (BillItem item : billList) {
                // Add Item
                psItem.setInt(1, billId);
                psItem.setInt(2, item.getMedicineId());
                psItem.setInt(3, item.getQty());
                psItem.setDouble(4, item.getPrice());
                psItem.setDouble(5, item.getTotal());
                psItem.addBatch();

                // Decrease Stock
                psStock.setInt(1, item.getQty());
                psStock.setInt(2, item.getMedicineId());
                psStock.addBatch();
            }

            psItem.executeBatch();
            psStock.executeBatch();

            conn.commit(); // Commit Transaction

            showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice Generated! ID: " + billId);

            // Cleanup
            billList.clear();
            updateTotal();
            loadInventory(); // Refresh stock in table
            loadKPIs();      // Refresh sales KPI

        } catch (Exception e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save invoice: " + e.getMessage());
        } finally {
            try { if (conn != null) conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
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

    // ---------------- Inner Classes ----------------

    public static class Medicine {
        private final int id; // Added ID for Database operations
        private final StringProperty name;
        private final StringProperty batch;
        private final StringProperty expiry;
        private final StringProperty hsn;
        private final IntegerProperty stock;
        private final double price; // Added Price for billing

        public Medicine(int id, String name, String batch, String expiry, String hsn, int stock, double price) {
            this.id = id;
            this.name = new SimpleStringProperty(name);
            this.batch = new SimpleStringProperty(batch);
            this.expiry = new SimpleStringProperty(expiry);
            this.hsn = new SimpleStringProperty(hsn);
            this.stock = new SimpleIntegerProperty(stock);
            this.price = price;
        }

        public StringProperty nameProperty() { return name; }
        public StringProperty batchProperty() { return batch; }
        public StringProperty expiryProperty() { return expiry; }
        public StringProperty hsnProperty() { return hsn; }
        public IntegerProperty stockProperty() { return stock; }

        public int getId() { return id; }
        public String getName() { return name.get(); }
        public int getStock() { return stock.get(); }
        public double getPrice() { return price; }
    }

    public static class BillItem {
        private final int medicineId; // Store ID to link back to DB
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

        public StringProperty nameProperty() { return name; }
        public IntegerProperty qtyProperty() { return qty; }
        public DoubleProperty priceProperty() { return price; }
        public DoubleProperty gstProperty() { return gst; }
        public DoubleProperty totalProperty() { return total; }

        public int getMedicineId() { return medicineId; }
        public int getQty() { return qty.get(); }
        public double getPrice() { return price.get(); }
        public double getTotal() { return total.get(); }
    }
}