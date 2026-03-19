package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.example.MediManage.dao.InventoryBatchDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.InventoryBatch;
import org.example.MediManage.util.AppExecutors;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class AlertsController {
    private static final int ALERT_LIMIT = 100;
    private static final int OUT_OF_STOCK_LOOKBACK_DAYS = 30;
    private static final double MARGIN_ALERT_THRESHOLD = 10.0;

    @FXML private ComboBox<Integer> comboWindowDays;

    @FXML private Label lblExpiredCount;
    @FXML private Label lblExpiringSoonCount;
    @FXML private Label lblReorderCount;
    @FXML private Label lblOutOfStockCount;
    @FXML private Label lblMarginRiskCount;
    @FXML private Label lblExpiryExposureValue;

    @FXML private TableView<InventoryBatch> expiredTable;
    @FXML private TableColumn<InventoryBatch, String> colExpiredMedicine;
    @FXML private TableColumn<InventoryBatch, String> colExpiredBatch;
    @FXML private TableColumn<InventoryBatch, String> colExpiredExpiry;
    @FXML private TableColumn<InventoryBatch, Number> colExpiredQty;
    @FXML private TableColumn<InventoryBatch, String> colExpiredCost;

    @FXML private TableView<InventoryBatch> expiringSoonTable;
    @FXML private TableColumn<InventoryBatch, String> colExpiringMedicine;
    @FXML private TableColumn<InventoryBatch, String> colExpiringSupplier;
    @FXML private TableColumn<InventoryBatch, String> colExpiringBatch;
    @FXML private TableColumn<InventoryBatch, String> colExpiringExpiry;
    @FXML private TableColumn<InventoryBatch, Number> colExpiringQty;

    @FXML private TableView<InventoryBatchDAO.ExpiryLossExposureRow> expiryExposureTable;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExposureMedicine;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExposureSupplier;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExposureBatch;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExposureExpiry;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, Number> colExposureQty;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExposureCost;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExposureSales;

    @FXML private TableView<MedicineDAO.ReorderNeededRow> reorderTable;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, String> colReorderMedicine;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, String> colReorderCompany;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, Number> colReorderStock;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, Number> colReorderThreshold;

    @FXML private TableView<MedicineDAO.OutOfStockInsightRow> outOfStockTable;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockMedicine;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockCompany;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, Number> colOutOfStockDays;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockRevenueRisk;

    @FXML private TableView<MedicineDAO.MarginRiskRow> marginRiskTable;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginMedicine;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginCompany;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, Number> colMarginStock;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginCost;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginPrice;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginUnit;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginPercent;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, Boolean> colMarginStatus;

    private final InventoryBatchDAO inventoryBatchDAO = new InventoryBatchDAO();
    private final MedicineDAO medicineDAO = new MedicineDAO();

    @FXML
    public void initialize() {
        comboWindowDays.setItems(FXCollections.observableArrayList(7, 15, 30, 60, 90));
        comboWindowDays.setValue(30);
        comboWindowDays.valueProperty().addListener((obs, oldVal, newVal) -> loadAlerts());
        setupTables();
        loadAlerts();
    }

    private void setupTables() {
        colExpiredMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colExpiredBatch.setCellValueFactory(d -> new SimpleStringProperty(formatBatchDisplay(
                d.getValue().expirySequence(),
                d.getValue().batchNumber(),
                d.getValue().batchBarcode())));
        colExpiredExpiry.setCellValueFactory(d -> new SimpleStringProperty(formatExpiryDisplay(
                d.getValue().expiryDate(),
                d.getValue().daysToExpiry())));
        colExpiredQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().availableQuantity()));
        colExpiredCost.setCellValueFactory(d -> new SimpleStringProperty(
                formatCurrency(d.getValue().availableQuantity() * d.getValue().unitCost())));

        colExpiringMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colExpiringSupplier.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().supplierName()));
        colExpiringBatch.setCellValueFactory(d -> new SimpleStringProperty(formatBatchDisplay(
                d.getValue().expirySequence(),
                d.getValue().batchNumber(),
                d.getValue().batchBarcode())));
        colExpiringExpiry.setCellValueFactory(d -> new SimpleStringProperty(formatExpiryDisplay(
                d.getValue().expiryDate(),
                d.getValue().daysToExpiry())));
        colExpiringQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().availableQuantity()));

        colExposureMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colExposureSupplier.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().supplierName()));
        colExposureBatch.setCellValueFactory(d -> new SimpleStringProperty(formatBatchDisplay(
                d.getValue().expirySequence(),
                d.getValue().batchNumber(),
                d.getValue().batchBarcode())));
        colExposureExpiry.setCellValueFactory(d -> new SimpleStringProperty(formatExpiryDisplay(
                d.getValue().expiryDate(),
                d.getValue().daysToExpiry())));
        colExposureQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().availableQuantity()));
        colExposureCost.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().stockCostValue())));
        colExposureSales.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().stockSalesValue())));

        colReorderMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colReorderCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
        colReorderStock.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().currentStock()));
        colReorderThreshold.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().reorderThreshold()));

        colOutOfStockMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colOutOfStockCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
        colOutOfStockDays.setCellValueFactory(d -> new SimpleIntegerProperty((int) d.getValue().daysOutOfStock()));
        colOutOfStockRevenueRisk.setCellValueFactory(d -> new SimpleStringProperty(
                formatCurrency(d.getValue().estimatedRevenueImpact())));

        colMarginMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colMarginCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
        colMarginStock.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().currentStock()));
        colMarginCost.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().purchasePrice())));
        colMarginPrice.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().sellingPrice())));
        colMarginUnit.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().unitMargin())));
        colMarginPercent.setCellValueFactory(d -> new SimpleStringProperty(
                String.format(Locale.ROOT, "%.1f%%", d.getValue().marginPercent())));
        colMarginStatus.setCellValueFactory(d -> new SimpleBooleanProperty(d.getValue().belowCost()));
        colMarginStatus.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item ? "Below Cost" : "Low Margin");
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadAlerts();
    }

    private void loadAlerts() {
        int windowDays = comboWindowDays.getValue() == null ? 30 : comboWindowDays.getValue();
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(Math.max(1, windowDays));

        AppExecutors.runBackground(() -> {
            try {
                List<InventoryBatch> expiredRows = inventoryBatchDAO.getExpiredBatches(today, ALERT_LIMIT);
                List<InventoryBatch> expiringRows = inventoryBatchDAO.getExpiringBatches(today, windowEnd, ALERT_LIMIT);
                List<InventoryBatchDAO.ExpiryLossExposureRow> exposureRows = inventoryBatchDAO
                        .getExpiryLossExposure(windowEnd, ALERT_LIMIT);
                List<MedicineDAO.ReorderNeededRow> reorderRows = medicineDAO.getReorderNeeded();
                List<MedicineDAO.OutOfStockInsightRow> outOfStockRows = medicineDAO
                        .getOutOfStockInsights(OUT_OF_STOCK_LOOKBACK_DAYS, ALERT_LIMIT);
                List<MedicineDAO.MarginRiskRow> marginRiskRows = medicineDAO
                        .getMarginRiskRows(MARGIN_ALERT_THRESHOLD, ALERT_LIMIT);

                int expiredCount = inventoryBatchDAO.countExpiredBatches(today);
                int expiringSoonCount = inventoryBatchDAO.countBatchesExpiringBetween(today, windowEnd);
                double exposureValue = inventoryBatchDAO.getExpiryLossExposureTotal(windowEnd);

                Platform.runLater(() -> {
                    expiredTable.setItems(FXCollections.observableArrayList(expiredRows));
                    expiringSoonTable.setItems(FXCollections.observableArrayList(expiringRows));
                    expiryExposureTable.setItems(FXCollections.observableArrayList(exposureRows));
                    reorderTable.setItems(FXCollections.observableArrayList(reorderRows));
                    outOfStockTable.setItems(FXCollections.observableArrayList(outOfStockRows));
                    marginRiskTable.setItems(FXCollections.observableArrayList(marginRiskRows));

                    lblExpiredCount.setText(String.valueOf(expiredCount));
                    lblExpiringSoonCount.setText(String.valueOf(expiringSoonCount));
                    lblReorderCount.setText(String.valueOf(reorderRows.size()));
                    lblOutOfStockCount.setText(String.valueOf(outOfStockRows.size()));
                    lblMarginRiskCount.setText(String.valueOf(marginRiskRows.size()));
                    lblExpiryExposureValue.setText(formatCurrency(exposureValue));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    expiredTable.setItems(FXCollections.observableArrayList());
                    expiringSoonTable.setItems(FXCollections.observableArrayList());
                    expiryExposureTable.setItems(FXCollections.observableArrayList());
                    reorderTable.setItems(FXCollections.observableArrayList());
                    outOfStockTable.setItems(FXCollections.observableArrayList());
                    marginRiskTable.setItems(FXCollections.observableArrayList());
                    lblExpiredCount.setText("0");
                    lblExpiringSoonCount.setText("0");
                    lblReorderCount.setText("0");
                    lblOutOfStockCount.setText("0");
                    lblMarginRiskCount.setText("0");
                    lblExpiryExposureValue.setText("Unavailable");
                });
            }
        });
    }

    private String formatCurrency(double value) {
        return String.format(Locale.ROOT, "₹%.2f", value);
    }

    private String formatBatchDisplay(int expirySequence, String batchNumber, String batchBarcode) {
        return "#" + expirySequence + " " + batchNumber + " | " + batchBarcode;
    }

    private String formatExpiryDisplay(String expiryDate, Integer daysToExpiry) {
        if (daysToExpiry == null) {
            return expiryDate;
        }
        return expiryDate + " (" + daysToExpiry + "d)";
    }
}
