package org.example.MediManage;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.Connection;
import java.sql.ResultSet;

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
            // Example KPIs (replace with your DB queries)
            ResultSet rs = conn.createStatement().executeQuery("SELECT IFNULL(SUM(total),0) AS daily_sales FROM bills WHERE date = DATE('now')");
            if (rs.next()) dailySales.setText("₹" + rs.getDouble("daily_sales"));

            rs = conn.createStatement().executeQuery("SELECT IFNULL(SUM(profit),0) AS total_profit FROM bills");
            if (rs.next()) totalProfit.setText("₹" + rs.getDouble("total_profit"));

            rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS pending FROM prescriptions WHERE status='pending'");
            if (rs.next()) pendingRx.setText(rs.getString("pending"));

            rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS low_stock FROM inventory WHERE stock < 10");
            if (rs.next()) lowStock.setText(rs.getString("low_stock"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadInventory() {
        try (Connection conn = DBUtil.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT name, batch, expiry, hsn, stock FROM inventory");
            while (rs.next()) {
                inventoryList.add(new Medicine(
                        rs.getString("name"),
                        rs.getString("batch"),
                        rs.getString("expiry"),
                        rs.getString("hsn"),
                        rs.getInt("stock")
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
        if (selected == null) return;

        BillItem item = new BillItem(selected.getName(), 1, 50.0, 5.0); // qty=1, price=50, GST=5
        billList.add(item);
        updateTotal();
    }

    private void generateInvoice() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Invoice");
        alert.setHeaderText("Invoice Generated ✅");
        alert.setContentText("Total: " + lblTotal.getText());
        alert.showAndWait();
        billList.clear();
        updateTotal();
    }

    private void updateTotal() {
        double total = billList.stream().mapToDouble(BillItem::getTotal).sum();
        lblTotal.setText("₹" + total);
    }

    // ---------------- Inner Classes ----------------
    public static class Medicine {
        private final StringProperty name;
        private final StringProperty batch;
        private final StringProperty expiry;
        private final StringProperty hsn;
        private final IntegerProperty stock;

        public Medicine(String name, String batch, String expiry, String hsn, int stock) {
            this.name = new SimpleStringProperty(name);
            this.batch = new SimpleStringProperty(batch);
            this.expiry = new SimpleStringProperty(expiry);
            this.hsn = new SimpleStringProperty(hsn);
            this.stock = new SimpleIntegerProperty(stock);
        }

        public StringProperty nameProperty() { return name; }
        public StringProperty batchProperty() { return batch; }
        public StringProperty expiryProperty() { return expiry; }
        public StringProperty hsnProperty() { return hsn; }
        public IntegerProperty stockProperty() { return stock; }

        public String getName() { return name.get(); }
        public int getStock() { return stock.get(); }
    }

    public static class BillItem {
        private final StringProperty name;
        private final IntegerProperty qty;
        private final DoubleProperty price;
        private final DoubleProperty gst;
        private final DoubleProperty total;

        public BillItem(String name, int qty, double price, double gst) {
            this.name = new SimpleStringProperty(name);
            this.qty = new SimpleIntegerProperty(qty);
            this.price = new SimpleDoubleProperty(price);
            this.gst = new SimpleDoubleProperty(gst);
            this.total = new SimpleDoubleProperty(price + gst);
        }

        public StringProperty nameProperty() { return name; }
        public IntegerProperty qtyProperty() { return qty; }
        public DoubleProperty priceProperty() { return price; }
        public DoubleProperty gstProperty() { return gst; }
        public DoubleProperty totalProperty() { return total; }

        public double getTotal() { return total.get(); }
    }
}
//has to add more
