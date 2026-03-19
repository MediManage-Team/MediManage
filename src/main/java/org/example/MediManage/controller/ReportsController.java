package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.InventoryBatchDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.PurchaseOrderDAO;
import org.example.MediManage.service.ReportService;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.util.AIHtmlRenderer;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.ReportingWindowUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportsController {
    private static final String AI_SUMMARY_READY_LABEL = "\u2728 Analyze Trends with AI";
    private static final String AI_SUMMARY_BUSY_LABEL = "\u23f3 Analyzing...";
    private static final int DEFAULT_INSIGHT_LIMIT = 50;
    private static final int REORDER_COVERAGE_DAYS = 14;
    private static final int DEAD_STOCK_IDLE_DAYS = 45;

    @FXML private DatePicker dateStart;
    @FXML private DatePicker dateEnd;

    // KPI Cards
    @FXML private Label lblTotalRevenue;
    @FXML private Label lblNetProfit;
    @FXML private Label lblTotalBills;
    @FXML private Label lblAvgOrderValue;
    @FXML private Label lblAvgMargin;

    // Charts
    @FXML private LineChart<String, Number> salesChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private PieChart paymentModesChart;
    @FXML private BarChart<String, Number> topVolumeChart;
    @FXML private BarChart<String, Number> topRevenueChart;
    @FXML private LineChart<String, Number> purchaseSalesChart;
    @FXML private CategoryAxis purchaseSalesXAxis;
    @FXML private NumberAxis purchaseSalesYAxis;

    // Data Table
    @FXML private TableView<ItemRow> itemTable;
    @FXML private TableColumn<ItemRow, String> colItemName;
    @FXML private TableColumn<ItemRow, Integer> colItemQty;
    @FXML private TableColumn<ItemRow, String> colItemRevenue;

    // Export
    @FXML private Button btnExportAnalytics;

    // Inventory signal summary
    @FXML private Label lblReorderCount;
    @FXML private Label lblOutOfStockCount;
    @FXML private Label lblNearStockOutCount;
    @FXML private Label lblDeadStockCount;
    @FXML private Label lblReturnDamageCount;
    @FXML private Label lblPurchaseSpend;
    @FXML private Label lblFastMoverCount;
    @FXML private Label lblMarginRiskCount;
    @FXML private Label lblExpiryExposureValue;
    @FXML private Label lblSupplierCoverage;
    @FXML private Label lblActiveBatchLines;
    @FXML private Label lblDumpedUnits;
    @FXML private Label lblStockGapCount;

    // Inventory signal tables
    @FXML private TableView<MedicineDAO.ReorderNeededRow> reorderTable;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, String> colReorderMedicine;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, String> colReorderCompany;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, Number> colReorderStock;
    @FXML private TableColumn<MedicineDAO.ReorderNeededRow, Number> colReorderThreshold;

    @FXML private TableView<MedicineDAO.OutOfStockInsightRow> outOfStockTable;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockMedicine;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockCompany;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, Number> colOutOfStockDays;
    @FXML private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockImpact;

    @FXML private TableView<MedicineDAO.NearStockOutInsightRow> nearStockOutTable;
    @FXML private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutMedicine;
    @FXML private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutCompany;
    @FXML private TableColumn<MedicineDAO.NearStockOutInsightRow, Number> colNearStockOutStock;
    @FXML private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutDays;
    @FXML private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutRisk;

    @FXML private TableView<MedicineDAO.DeadStockInsightRow> deadStockTable;
    @FXML private TableColumn<MedicineDAO.DeadStockInsightRow, String> colDeadStockMedicine;
    @FXML private TableColumn<MedicineDAO.DeadStockInsightRow, String> colDeadStockCompany;
    @FXML private TableColumn<MedicineDAO.DeadStockInsightRow, Number> colDeadStockDays;
    @FXML private TableColumn<MedicineDAO.DeadStockInsightRow, String> colDeadStockValue;

    @FXML private TableView<MedicineDAO.ReturnDamagedInsightRow> returnDamageTable;
    @FXML private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamageMedicine;
    @FXML private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamageCompany;
    @FXML private TableColumn<MedicineDAO.ReturnDamagedInsightRow, Number> colReturnDamageQty;
    @FXML private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamageValue;
    @FXML private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamageTags;

    @FXML private TableView<MedicineDAO.FastMovingInsightRow> fastMovingTable;
    @FXML private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingMedicine;
    @FXML private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingCompany;
    @FXML private TableColumn<MedicineDAO.FastMovingInsightRow, Number> colFastMovingUnits;
    @FXML private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingRevenue;

    @FXML private TableView<MedicineDAO.MarginRiskRow> marginRiskTable;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginRiskMedicine;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginRiskCompany;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, Number> colMarginRiskStock;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginRiskCost;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginRiskSell;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginRiskUnit;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginRiskPercent;
    @FXML private TableColumn<MedicineDAO.MarginRiskRow, String> colMarginRiskStatus;

    @FXML private TableView<InventoryBatchDAO.ExpiryLossExposureRow> expiryExposureTable;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExpiryExposureMedicine;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExpiryExposureSupplier;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExpiryExposureBatch;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExpiryExposureDate;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, Number> colExpiryExposureQty;
    @FXML private TableColumn<InventoryBatchDAO.ExpiryLossExposureRow, String> colExpiryExposureCost;

    @FXML private TableView<PurchaseOrderDAO.SupplierPerformanceRow> supplierPerformanceTable;
    @FXML private TableColumn<PurchaseOrderDAO.SupplierPerformanceRow, String> colSupplierPerformanceName;
    @FXML private TableColumn<PurchaseOrderDAO.SupplierPerformanceRow, Number> colSupplierPerformanceOrders;
    @FXML private TableColumn<PurchaseOrderDAO.SupplierPerformanceRow, Number> colSupplierPerformanceSkus;
    @FXML private TableColumn<PurchaseOrderDAO.SupplierPerformanceRow, Number> colSupplierPerformanceUnits;
    @FXML private TableColumn<PurchaseOrderDAO.SupplierPerformanceRow, String> colSupplierPerformanceSpend;
    @FXML private TableColumn<PurchaseOrderDAO.SupplierPerformanceRow, String> colSupplierPerformanceAvgCost;
    @FXML private TableColumn<PurchaseOrderDAO.SupplierPerformanceRow, String> colSupplierPerformanceLastOrder;

    @FXML private TableView<InventoryBatchDAO.MedicineManagementOverviewRow> managementOverviewTable;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, String> colManagementMedicine;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, String> colManagementBarcode;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, String> colManagementStock;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, Number> colManagementGap;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, Number> colManagementBatches;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, String> colManagementExpiry;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, Number> colManagementExpiring;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, Number> colManagementExpired;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, Number> colManagementDumped;
    @FXML private TableColumn<InventoryBatchDAO.MedicineManagementOverviewRow, String> colManagementExposure;

    // AI
    @FXML private Button btnAISummary;
    @FXML private WebView txtAISummary;
    @FXML private javafx.scene.control.ProgressIndicator spinnerSummary;

    private final BillDAO billDAO = new BillDAO();
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final InventoryBatchDAO inventoryBatchDAO = new InventoryBatchDAO();
    private final PurchaseOrderDAO purchaseOrderDAO = new PurchaseOrderDAO();
    private final ReportService reportService = new ReportService();
    private final AIAssistantService aiService = new AIAssistantService();

    private Map<String, Double> lastSalesData = new HashMap<>();
    private Map<String, Double> lastPurchaseSpendData = new HashMap<>();
    private Map<String, Integer> lastItemVolume = new HashMap<>();
    private Map<String, Double> lastItemRevenue = new HashMap<>();
    private double lastTotalRevenue = 0;
    private List<MedicineDAO.ReorderNeededRow> lastReorderNeeded = List.of();
    private List<MedicineDAO.OutOfStockInsightRow> lastOutOfStock = List.of();
    private List<MedicineDAO.NearStockOutInsightRow> lastNearStockOut = List.of();
    private List<MedicineDAO.DeadStockInsightRow> lastDeadStock = List.of();
    private List<MedicineDAO.ReturnDamagedInsightRow> lastReturnDamaged = List.of();
    private List<MedicineDAO.FastMovingInsightRow> lastFastMoving = List.of();
    private List<MedicineDAO.MarginRiskRow> lastMarginRisk = List.of();
    private List<InventoryBatchDAO.ExpiryLossExposureRow> lastExpiryExposure = List.of();
    private List<PurchaseOrderDAO.SupplierPerformanceRow> lastSupplierPerformance = List.of();
    private List<InventoryBatchDAO.MedicineManagementOverviewRow> lastManagementOverview = List.of();
    private boolean keyboardShortcutsRegistered = false;

    public static class ItemRow {
        private final String name;
        private final int qty;
        private final double revenue;

        public ItemRow(String name, int qty, double revenue) {
            this.name = name;
            this.qty = qty;
            this.revenue = revenue;
        }

        public String getName() {
            return name;
        }

        public int getQty() {
            return qty;
        }

        public double getRevenue() {
            return revenue;
        }
    }

    @FXML
    public void initialize() {
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils
                .currentMondayToSunday(ZoneId.systemDefault());
        dateStart.setValue(weeklyWindow.startDate());
        dateEnd.setValue(weeklyWindow.endDate());
        if (txtAISummary != null) {
            txtAISummary.setContextMenuEnabled(false);
            txtAISummary.setStyle("-fx-background-color: transparent;");
        }

        setupItemTable();
        setupInsightTables();
        setupOperationalTables();
        loadReport();
        setAiSummaryContent("Generate a report, then run AI analysis to get patient-care and business insights.");
        setupKeyboardShortcuts();
    }

    private void setupItemTable() {
        colItemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colItemQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getQty()).asObject());
        colItemRevenue.setCellValueFactory(
                d -> new SimpleStringProperty(formatCurrency(d.getValue().getRevenue())));
    }

    private void setupInsightTables() {
        if (colReorderMedicine != null) {
            colReorderMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colReorderCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
            colReorderStock.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().currentStock()));
            colReorderThreshold.setCellValueFactory(
                    d -> new SimpleIntegerProperty(d.getValue().reorderThreshold()));
        }

        if (colOutOfStockMedicine != null) {
            colOutOfStockMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colOutOfStockCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
            colOutOfStockDays.setCellValueFactory(
                    d -> new SimpleIntegerProperty((int) d.getValue().daysOutOfStock()));
            colOutOfStockImpact.setCellValueFactory(
                    d -> new SimpleStringProperty(formatCurrency(d.getValue().estimatedRevenueImpact())));
        }

        if (colNearStockOutMedicine != null) {
            colNearStockOutMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colNearStockOutCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
            colNearStockOutStock.setCellValueFactory(
                    d -> new SimpleIntegerProperty(d.getValue().currentStock()));
            colNearStockOutDays.setCellValueFactory(
                    d -> new SimpleStringProperty(String.format(Locale.ROOT, "%.1f", d.getValue().daysToStockOut())));
            colNearStockOutRisk.setCellValueFactory(
                    d -> new SimpleStringProperty(formatCurrency(d.getValue().estimatedRevenueAtRisk())));
        }

        if (colDeadStockMedicine != null) {
            colDeadStockMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colDeadStockCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
            colDeadStockDays.setCellValueFactory(
                    d -> new SimpleIntegerProperty((int) d.getValue().daysSinceLastMovement()));
            colDeadStockValue.setCellValueFactory(
                    d -> new SimpleStringProperty(formatCurrency(d.getValue().deadStockValue())));
        }

        if (colReturnDamageMedicine != null) {
            colReturnDamageMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colReturnDamageCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
            colReturnDamageQty.setCellValueFactory(
                    d -> new SimpleIntegerProperty((int) d.getValue().totalQuantity()));
            colReturnDamageValue.setCellValueFactory(
                    d -> new SimpleStringProperty(formatCurrency(d.getValue().totalValue())));
            colReturnDamageTags.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().rootCauseTags()));
        }
    }

    private void setupOperationalTables() {
        if (colFastMovingMedicine != null) {
            colFastMovingMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colFastMovingCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
            colFastMovingUnits.setCellValueFactory(d -> new SimpleIntegerProperty((int) Math.round(d.getValue().lookbackUnitsSold())));
            colFastMovingRevenue.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().lookbackRevenue())));
        }

        if (colMarginRiskMedicine != null) {
            colMarginRiskMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colMarginRiskCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
            colMarginRiskStock.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().currentStock()));
            colMarginRiskCost.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().purchasePrice())));
            colMarginRiskSell.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().sellingPrice())));
            colMarginRiskUnit.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().unitMargin())));
            colMarginRiskPercent.setCellValueFactory(d -> new SimpleStringProperty(
                    String.format(Locale.ROOT, "%.1f%%", d.getValue().marginPercent())));
            colMarginRiskStatus.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().belowCost() ? "Below Cost" : "Low Margin"));
        }

        if (colExpiryExposureMedicine != null) {
            colExpiryExposureMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colExpiryExposureSupplier.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().supplierName()));
            colExpiryExposureBatch.setCellValueFactory(d -> new SimpleStringProperty(formatBatchDisplay(
                    d.getValue().expirySequence(),
                    d.getValue().batchNumber(),
                    d.getValue().batchBarcode())));
            colExpiryExposureDate.setCellValueFactory(d -> new SimpleStringProperty(formatExpiryDisplay(
                    d.getValue().expiryDate(),
                    d.getValue().daysToExpiry())));
            colExpiryExposureQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().availableQuantity()));
            colExpiryExposureCost.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().stockCostValue())));
        }

        if (colSupplierPerformanceName != null) {
            colSupplierPerformanceName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().supplierName()));
            colSupplierPerformanceOrders.setCellValueFactory(d -> new SimpleIntegerProperty((int) d.getValue().purchaseOrders()));
            colSupplierPerformanceSkus.setCellValueFactory(d -> new SimpleIntegerProperty((int) d.getValue().distinctSkus()));
            colSupplierPerformanceUnits.setCellValueFactory(d -> new SimpleIntegerProperty((int) d.getValue().totalUnits()));
            colSupplierPerformanceSpend.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().totalSpend())));
            colSupplierPerformanceAvgCost.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().averageUnitCost())));
            colSupplierPerformanceLastOrder.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().lastOrderDate() == null ? "-" : d.getValue().lastOrderDate()));
        }

        if (colManagementMedicine != null) {
            colManagementMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
            colManagementBarcode.setCellValueFactory(d -> new SimpleStringProperty(safeText(d.getValue().medicineBarcode())));
            colManagementStock.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().currentStock() + " live / " + d.getValue().trackedBatchUnits() + " tracked"));
            colManagementGap.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().stockGapUnits()));
            colManagementBatches.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().activeBatchCount()));
            colManagementExpiry.setCellValueFactory(d -> new SimpleStringProperty(formatOverviewExpiry(d.getValue())));
            colManagementExpiring.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().expiring30dUnits()));
            colManagementExpired.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().expiredUnits()));
            colManagementDumped.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().dumpedUnits()));
            colManagementExposure.setCellValueFactory(d -> new SimpleStringProperty(formatCurrency(d.getValue().expiryExposureCost())));
        }
    }

    @FXML
    private void handleGenerate() {
        loadReport();
    }

    @FXML
    private void handleExportAnalytics() {
        LocalDate start = dateStart.getValue();
        LocalDate end = dateEnd.getValue();
        if (start == null || end == null || start.isAfter(end)) {
            showAlert(Alert.AlertType.WARNING, "Invalid Range", "Choose a valid date range before exporting.");
            return;
        }

        ChoiceDialog<String> formatDialog = new ChoiceDialog<>("PDF", List.of("PDF", "EXCEL", "CSV"));
        formatDialog.setTitle("Export Analytics");
        formatDialog.setHeaderText("Choose export format");
        formatDialog.setContentText("Format:");
        String selectedFormat = formatDialog.showAndWait().orElse(null);
        if (selectedFormat == null) {
            return;
        }

        ReportService.AnalyticsExportFormat exportFormat = ReportService.AnalyticsExportFormat.fromValue(selectedFormat);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Analytics Report");
        chooser.setInitialFileName("analytics_" + start + "_to_" + end + "." + exportFormat.fileExtension());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                exportFormat.name() + " (*." + exportFormat.fileExtension() + ")",
                "*." + exportFormat.fileExtension()));

        File file = chooser.showSaveDialog(btnExportAnalytics == null ? null : btnExportAnalytics.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            reportService.exportAnalyticsReport(buildExportPayload(start, end), exportFormat, file.getAbsolutePath());
            showAlert(Alert.AlertType.INFORMATION, "Export Complete", "Analytics report saved to " + file.getName() + ".");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
        }
    }

    private void loadReport() {
        LocalDate start = dateStart.getValue();
        LocalDate end = dateEnd.getValue();
        if (start == null || end == null || start.isAfter(end)) {
            return;
        }

        AppExecutors.runBackground(() -> {
            Map<String, Double> salesByDay = billDAO.getSalesBetweenDates(start, end);
            Map<String, Double> profitByDay = billDAO.getProfitBetweenDates(start, end);
            Map<String, Double> purchaseSpendByDay = purchaseOrderDAO.getPurchaseSpendBetweenDates(start, end);
            Map<String, Integer> paymentModes = billDAO.getPaymentMethodDistribution(start, end);
            Map<String, Integer> itemVolume = billDAO.getItemizedSales(start, end);
            Map<String, Double> itemRevenue = billDAO.getItemizedRevenue(start, end);

            List<MedicineDAO.ReorderNeededRow> reorderRows = medicineDAO.getReorderNeeded();
            List<MedicineDAO.OutOfStockInsightRow> outOfStockRows = medicineDAO
                    .getOutOfStockInsights(start, end, null, null, DEFAULT_INSIGHT_LIMIT);
            List<MedicineDAO.NearStockOutInsightRow> nearStockOutRows = medicineDAO
                    .getNearStockOutInsights(start, end, REORDER_COVERAGE_DAYS, null, null, DEFAULT_INSIGHT_LIMIT);
            List<MedicineDAO.DeadStockInsightRow> deadStockRows = medicineDAO
                    .getDeadStockInsights(end, DEAD_STOCK_IDLE_DAYS, null, null, DEFAULT_INSIGHT_LIMIT);
            List<MedicineDAO.ReturnDamagedInsightRow> returnDamagedRows = medicineDAO
                    .getReturnDamagedInsights(start, end, null, null, DEFAULT_INSIGHT_LIMIT);
            List<MedicineDAO.FastMovingInsightRow> fastMovingRows = medicineDAO
                    .getFastMovingInsights(start, end, null, null, DEFAULT_INSIGHT_LIMIT);
            List<MedicineDAO.MarginRiskRow> marginRiskRows = medicineDAO
                    .getMarginRiskRows(10.0, DEFAULT_INSIGHT_LIMIT);
            List<InventoryBatchDAO.ExpiryLossExposureRow> expiryExposureRows;
            double expiryExposureTotal;
            try {
                expiryExposureRows = inventoryBatchDAO.getExpiryLossExposure(LocalDate.now().plusDays(30), DEFAULT_INSIGHT_LIMIT);
                expiryExposureTotal = inventoryBatchDAO.getExpiryLossExposureTotal(LocalDate.now().plusDays(30));
            } catch (Exception e) {
                expiryExposureRows = List.of();
                expiryExposureTotal = 0.0;
            }
            List<PurchaseOrderDAO.SupplierPerformanceRow> supplierPerformanceRows = purchaseOrderDAO
                    .getSupplierPerformance(start, end, DEFAULT_INSIGHT_LIMIT);
            List<InventoryBatchDAO.MedicineManagementOverviewRow> managementOverviewRows;
            try {
                managementOverviewRows = inventoryBatchDAO.getManagementOverview(DEFAULT_INSIGHT_LIMIT);
            } catch (Exception e) {
                managementOverviewRows = List.of();
            }
            final List<InventoryBatchDAO.ExpiryLossExposureRow> finalExpiryExposureRows = expiryExposureRows;
            final double finalExpiryExposureTotal = expiryExposureTotal;
            final List<InventoryBatchDAO.MedicineManagementOverviewRow> finalManagementOverviewRows = managementOverviewRows;

            double totalRev = salesByDay.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalProf = profitByDay.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalPurchaseSpend = purchaseSpendByDay.values().stream().mapToDouble(Double::doubleValue).sum();
            int totalTxns = paymentModes.values().stream().mapToInt(Integer::intValue).sum();
            double aov = totalTxns > 0 ? totalRev / totalTxns : 0.0;
            double avgMargin = totalRev > 0 ? (totalProf / totalRev) * 100 : 0.0;
            int activeBatchLines = finalManagementOverviewRows.stream()
                    .mapToInt(InventoryBatchDAO.MedicineManagementOverviewRow::activeBatchCount)
                    .sum();
            int dumpedUnits = finalManagementOverviewRows.stream()
                    .mapToInt(InventoryBatchDAO.MedicineManagementOverviewRow::dumpedUnits)
                    .sum();
            long stockGapCount = finalManagementOverviewRows.stream()
                    .filter(row -> row.stockGapUnits() != 0)
                    .count();

            Platform.runLater(() -> {
                lblTotalRevenue.setText(formatCurrency(totalRev));
                lblNetProfit.setText(formatCurrency(totalProf));
                lblTotalBills.setText(String.valueOf(totalTxns));
                lblAvgOrderValue.setText(formatCurrency(aov));
                lblAvgMargin.setText(String.format(Locale.ROOT, "%.1f%%", avgMargin));
                if (lblPurchaseSpend != null) lblPurchaseSpend.setText(formatCurrency(totalPurchaseSpend));
                if (lblFastMoverCount != null) lblFastMoverCount.setText(String.valueOf(fastMovingRows.size()));
                if (lblMarginRiskCount != null) lblMarginRiskCount.setText(String.valueOf(marginRiskRows.size()));
                if (lblExpiryExposureValue != null) lblExpiryExposureValue.setText(formatCurrency(finalExpiryExposureTotal));
                if (lblSupplierCoverage != null) lblSupplierCoverage.setText(String.valueOf(supplierPerformanceRows.size()));
                if (lblActiveBatchLines != null) lblActiveBatchLines.setText(String.valueOf(activeBatchLines));
                if (lblDumpedUnits != null) lblDumpedUnits.setText(String.valueOf(dumpedUnits));
                if (lblStockGapCount != null) lblStockGapCount.setText(String.valueOf(stockGapCount));

                updateSalesCharts(salesByDay, profitByDay, paymentModes, itemVolume, itemRevenue);
                updatePurchaseSalesChart(start, end, salesByDay, purchaseSpendByDay);
                updateItemTable(itemVolume, itemRevenue);
                updateInsightTables(reorderRows, outOfStockRows, nearStockOutRows, deadStockRows, returnDamagedRows);
                updateOperationalTables(
                        fastMovingRows,
                        marginRiskRows,
                        finalExpiryExposureRows,
                        supplierPerformanceRows,
                        finalManagementOverviewRows);

                lastSalesData = salesByDay;
                lastPurchaseSpendData = purchaseSpendByDay;
                lastItemVolume = itemVolume;
                lastItemRevenue = itemRevenue;
                lastTotalRevenue = totalRev;
                lastReorderNeeded = List.copyOf(reorderRows);
                lastOutOfStock = List.copyOf(outOfStockRows);
                lastNearStockOut = List.copyOf(nearStockOutRows);
                lastDeadStock = List.copyOf(deadStockRows);
                lastReturnDamaged = List.copyOf(returnDamagedRows);
                lastFastMoving = List.copyOf(fastMovingRows);
                lastMarginRisk = List.copyOf(marginRiskRows);
                lastExpiryExposure = List.copyOf(finalExpiryExposureRows);
                lastSupplierPerformance = List.copyOf(supplierPerformanceRows);
                lastManagementOverview = List.copyOf(finalManagementOverviewRows);
            });
        });
    }

    private void updateSalesCharts(
            Map<String, Double> salesByDay,
            Map<String, Double> profitByDay,
            Map<String, Integer> paymentModes,
            Map<String, Integer> itemVolume,
            Map<String, Double> itemRevenue) {
        salesChart.getData().clear();
        XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
        revenueSeries.setName("Revenue");
        for (Map.Entry<String, Double> entry : salesByDay.entrySet()) {
            revenueSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        XYChart.Series<String, Number> profitSeries = new XYChart.Series<>();
        profitSeries.setName("Net Profit");
        for (Map.Entry<String, Double> entry : profitByDay.entrySet()) {
            profitSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        salesChart.getData().add(revenueSeries);
        salesChart.getData().add(profitSeries);

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Map.Entry<String, Integer> entry : paymentModes.entrySet()) {
            pieData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }
        paymentModesChart.setData(pieData);

        topVolumeChart.getData().clear();
        XYChart.Series<String, Number> volumeSeries = new XYChart.Series<>();
        volumeSeries.setName("Units Sold");
        int count = 0;
        for (Map.Entry<String, Integer> entry : itemVolume.entrySet()) {
            if (count++ >= 5) {
                break;
            }
            volumeSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        topVolumeChart.getData().add(volumeSeries);

        topRevenueChart.getData().clear();
        XYChart.Series<String, Number> revenueTopSeries = new XYChart.Series<>();
        revenueTopSeries.setName("Revenue Generated");
        count = 0;
        for (Map.Entry<String, Double> entry : itemRevenue.entrySet()) {
            if (count++ >= 5) {
                break;
            }
            revenueTopSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        topRevenueChart.getData().add(revenueTopSeries);
    }

    private void updatePurchaseSalesChart(
            LocalDate start,
            LocalDate end,
            Map<String, Double> salesByDay,
            Map<String, Double> purchaseSpendByDay) {
        if (purchaseSalesChart == null) {
            return;
        }

        purchaseSalesChart.getData().clear();
        XYChart.Series<String, Number> salesSeries = new XYChart.Series<>();
        salesSeries.setName("Sales");
        XYChart.Series<String, Number> purchaseSeries = new XYChart.Series<>();
        purchaseSeries.setName("Purchase Spend");

        LocalDate safeStart = start == null ? LocalDate.now() : start;
        LocalDate safeEnd = end == null ? safeStart : end;
        if (safeEnd.isBefore(safeStart)) {
            safeEnd = safeStart;
        }

        for (LocalDate day = safeStart; !day.isAfter(safeEnd); day = day.plusDays(1)) {
            String key = day.toString();
            salesSeries.getData().add(new XYChart.Data<>(key, salesByDay.getOrDefault(key, 0.0)));
            purchaseSeries.getData().add(new XYChart.Data<>(key, purchaseSpendByDay.getOrDefault(key, 0.0)));
        }

        purchaseSalesChart.getData().setAll(List.of(salesSeries, purchaseSeries));
    }

    private void updateItemTable(Map<String, Integer> itemVolume, Map<String, Double> itemRevenue) {
        ObservableList<ItemRow> tableRows = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : itemRevenue.entrySet()) {
            String medicineName = entry.getKey();
            int qty = itemVolume.getOrDefault(medicineName, 0);
            tableRows.add(new ItemRow(medicineName, qty, entry.getValue()));
        }
        itemTable.setItems(tableRows);
    }

    private void updateInsightTables(
            List<MedicineDAO.ReorderNeededRow> reorderRows,
            List<MedicineDAO.OutOfStockInsightRow> outOfStockRows,
            List<MedicineDAO.NearStockOutInsightRow> nearStockOutRows,
            List<MedicineDAO.DeadStockInsightRow> deadStockRows,
            List<MedicineDAO.ReturnDamagedInsightRow> returnDamagedRows) {
        if (reorderTable != null) {
            reorderTable.setItems(FXCollections.observableArrayList(reorderRows));
        }
        if (outOfStockTable != null) {
            outOfStockTable.setItems(FXCollections.observableArrayList(outOfStockRows));
        }
        if (nearStockOutTable != null) {
            nearStockOutTable.setItems(FXCollections.observableArrayList(nearStockOutRows));
        }
        if (deadStockTable != null) {
            deadStockTable.setItems(FXCollections.observableArrayList(deadStockRows));
        }
        if (returnDamageTable != null) {
            returnDamageTable.setItems(FXCollections.observableArrayList(returnDamagedRows));
        }

        if (lblReorderCount != null) lblReorderCount.setText(String.valueOf(reorderRows.size()));
        if (lblOutOfStockCount != null) lblOutOfStockCount.setText(String.valueOf(outOfStockRows.size()));
        if (lblNearStockOutCount != null) lblNearStockOutCount.setText(String.valueOf(nearStockOutRows.size()));
        if (lblDeadStockCount != null) lblDeadStockCount.setText(String.valueOf(deadStockRows.size()));
        if (lblReturnDamageCount != null) lblReturnDamageCount.setText(String.valueOf(returnDamagedRows.size()));
    }

    private void updateOperationalTables(
            List<MedicineDAO.FastMovingInsightRow> fastMovingRows,
            List<MedicineDAO.MarginRiskRow> marginRiskRows,
            List<InventoryBatchDAO.ExpiryLossExposureRow> expiryExposureRows,
            List<PurchaseOrderDAO.SupplierPerformanceRow> supplierPerformanceRows,
            List<InventoryBatchDAO.MedicineManagementOverviewRow> managementOverviewRows) {
        if (fastMovingTable != null) {
            fastMovingTable.setItems(FXCollections.observableArrayList(fastMovingRows));
        }
        if (marginRiskTable != null) {
            marginRiskTable.setItems(FXCollections.observableArrayList(marginRiskRows));
        }
        if (expiryExposureTable != null) {
            expiryExposureTable.setItems(FXCollections.observableArrayList(expiryExposureRows));
        }
        if (supplierPerformanceTable != null) {
            supplierPerformanceTable.setItems(FXCollections.observableArrayList(supplierPerformanceRows));
        }
        if (managementOverviewTable != null) {
            managementOverviewTable.setItems(FXCollections.observableArrayList(managementOverviewRows));
        }
    }

    @FXML
    private void handleAISummary() {
        if (lastSalesData.isEmpty()) {
            setAiSummaryContent("No sales data available. Generate a report first.");
            return;
        }
        btnAISummary.setDisable(true);
        btnAISummary.setText(AI_SUMMARY_BUSY_LABEL);
        setSpinnerVisible(true);
        setAiSummaryContent("\u23f3 Running AI sales analysis...");

        aiService.generateReportSummary(lastSalesData, lastTotalRevenue, lastItemVolume)
                .thenAccept(result -> Platform.runLater(() -> {
                    btnAISummary.setDisable(false);
                    btnAISummary.setText(AI_SUMMARY_READY_LABEL);
                    setSpinnerVisible(false);
                    setAiSummaryContent(result);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        btnAISummary.setDisable(false);
                        btnAISummary.setText(AI_SUMMARY_READY_LABEL);
                        setSpinnerVisible(false);
                        setAiSummaryContent("❌ Request failed.\n" + rootCauseMessage(ex)
                                + "\n\nPlease retry once the AI engine or cloud route is available.");
                    });
                    return null;
                });
    }

    private ReportService.AnalyticsExportPayload buildExportPayload(LocalDate start, LocalDate end) {
        List<String> summaryLines = List.of(
                "Total Revenue: " + safeLabel(lblTotalRevenue),
                "Net Profit: " + safeLabel(lblNetProfit),
                "Total Bills: " + safeLabel(lblTotalBills),
                "Average Order Value: " + safeLabel(lblAvgOrderValue),
                "Average Margin: " + safeLabel(lblAvgMargin),
                "Purchase Spend: " + safeLabel(lblPurchaseSpend),
                "Fast Movers: " + safeLabel(lblFastMoverCount),
                "Margin Risk: " + safeLabel(lblMarginRiskCount),
                "Expiry Loss Exposure: " + safeLabel(lblExpiryExposureValue),
                "Supplier Coverage: " + safeLabel(lblSupplierCoverage),
                "Active Batch Lines: " + safeLabel(lblActiveBatchLines),
                "Dumped Units: " + safeLabel(lblDumpedUnits),
                "Stock Gaps: " + safeLabel(lblStockGapCount),
                "Reorder Needed: " + safeLabel(lblReorderCount),
                "Out of Stock: " + safeLabel(lblOutOfStockCount),
                "Near Stockout: " + safeLabel(lblNearStockOutCount),
                "Dead Stock: " + safeLabel(lblDeadStockCount),
                "Returns / Damage / Dump: " + safeLabel(lblReturnDamageCount));

        List<ReportService.AnalyticsReportSection> sections = new ArrayList<>();
        sections.add(new ReportService.AnalyticsReportSection(
                "Detailed Item Analytics",
                List.of("Medicine", "Units Sold", "Revenue"),
                buildItemAnalyticsRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Sales Vs Purchase Spend",
                List.of("Date", "Sales", "Purchase Spend", "Operating Gap"),
                buildPurchaseSpendRows(start, end)));
        sections.add(new ReportService.AnalyticsReportSection(
                "Fast Movers",
                List.of("Medicine", "Company", "Units Sold", "Revenue"),
                buildFastMovingRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Margin Risk",
                List.of("Medicine", "Company", "Stock", "Cost", "Sell Price", "Unit Margin", "Margin %", "Status"),
                buildMarginRiskRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Expiry Loss Exposure",
                List.of("Medicine", "Supplier", "Batch / Barcode", "Expiry Line", "Qty", "Cost Value", "Sales Value"),
                buildExpiryExposureRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Medicine Management Overview",
                List.of("Medicine", "Barcode", "Stock / Batch", "Gap", "Active Batches", "Earliest Expiry", "Expiring 30d", "Expired", "Dumped", "Exposure"),
                buildManagementOverviewRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Supplier Performance",
                List.of("Supplier", "Orders", "SKUs", "Units", "Spend", "Average Unit Cost", "Last Order"),
                buildSupplierPerformanceRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Reorder Needed",
                List.of("Medicine", "Company", "Current Stock", "Threshold"),
                buildReorderRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Out Of Stock",
                List.of("Medicine", "Company", "Days Out", "Revenue Impact"),
                buildOutOfStockRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Near Stockout",
                List.of("Medicine", "Company", "Current Stock", "Days To Stockout", "Revenue Risk"),
                buildNearStockOutRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Dead Stock",
                List.of("Medicine", "Company", "Days Idle", "Dead Stock Value"),
                buildDeadStockRows()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Returns And Damage And Dump",
                List.of("Medicine", "Company", "Total Qty", "Total Value", "Root Causes"),
                buildReturnDamageRows()));

        return new ReportService.AnalyticsExportPayload(
                "MediManage Analytics Report",
                java.time.LocalDateTime.now().toString(),
                "Range: " + start + " to " + end,
                summaryLines,
                sections);
    }

    private List<List<String>> buildItemAnalyticsRows() {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, Double> entry : lastItemRevenue.entrySet()) {
            rows.add(List.of(
                    entry.getKey(),
                    String.valueOf(lastItemVolume.getOrDefault(entry.getKey(), 0)),
                    formatCurrency(entry.getValue())));
        }
        return rows;
    }

    private List<List<String>> buildPurchaseSpendRows(LocalDate start, LocalDate end) {
        List<List<String>> rows = new ArrayList<>();
        LocalDate safeStart = start == null ? LocalDate.now() : start;
        LocalDate safeEnd = end == null ? safeStart : end;
        if (safeEnd.isBefore(safeStart)) {
            safeEnd = safeStart;
        }
        for (LocalDate day = safeStart; !day.isAfter(safeEnd); day = day.plusDays(1)) {
            String key = day.toString();
            double sales = lastSalesData.getOrDefault(key, 0.0);
            double purchases = lastPurchaseSpendData.getOrDefault(key, 0.0);
            rows.add(List.of(
                    key,
                    formatCurrency(sales),
                    formatCurrency(purchases),
                    formatCurrency(sales - purchases)));
        }
        return rows;
    }

    private List<List<String>> buildFastMovingRows() {
        return lastFastMoving.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.company(),
                        String.format(Locale.ROOT, "%.0f", row.lookbackUnitsSold()),
                        formatCurrency(row.lookbackRevenue())))
                .toList();
    }

    private List<List<String>> buildMarginRiskRows() {
        return lastMarginRisk.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.company(),
                        String.valueOf(row.currentStock()),
                        formatCurrency(row.purchasePrice()),
                        formatCurrency(row.sellingPrice()),
                        formatCurrency(row.unitMargin()),
                        String.format(Locale.ROOT, "%.1f%%", row.marginPercent()),
                        row.belowCost() ? "Below Cost" : "Low Margin"))
                .toList();
    }

    private List<List<String>> buildExpiryExposureRows() {
        return lastExpiryExposure.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.supplierName(),
                        formatBatchDisplay(row.expirySequence(), row.batchNumber(), row.batchBarcode()),
                        formatExpiryDisplay(row.expiryDate(), row.daysToExpiry()),
                        String.valueOf(row.availableQuantity()),
                        formatCurrency(row.stockCostValue()),
                        formatCurrency(row.stockSalesValue())))
                .toList();
    }

    private List<List<String>> buildManagementOverviewRows() {
        return lastManagementOverview.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        safeText(row.medicineBarcode()),
                        row.currentStock() + " live / " + row.trackedBatchUnits() + " tracked",
                        String.valueOf(row.stockGapUnits()),
                        String.valueOf(row.activeBatchCount()),
                        formatOverviewExpiry(row),
                        String.valueOf(row.expiring30dUnits()),
                        String.valueOf(row.expiredUnits()),
                        String.valueOf(row.dumpedUnits()),
                        formatCurrency(row.expiryExposureCost())))
                .toList();
    }

    private List<List<String>> buildSupplierPerformanceRows() {
        return lastSupplierPerformance.stream()
                .map(row -> List.of(
                        row.supplierName(),
                        String.valueOf(row.purchaseOrders()),
                        String.valueOf(row.distinctSkus()),
                        String.valueOf(row.totalUnits()),
                        formatCurrency(row.totalSpend()),
                        formatCurrency(row.averageUnitCost()),
                        row.lastOrderDate() == null ? "-" : row.lastOrderDate()))
                .toList();
    }

    private List<List<String>> buildReorderRows() {
        return lastReorderNeeded.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.company(),
                        String.valueOf(row.currentStock()),
                        String.valueOf(row.reorderThreshold())))
                .toList();
    }

    private List<List<String>> buildOutOfStockRows() {
        return lastOutOfStock.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.company(),
                        String.valueOf(row.daysOutOfStock()),
                        formatCurrency(row.estimatedRevenueImpact())))
                .toList();
    }

    private List<List<String>> buildNearStockOutRows() {
        return lastNearStockOut.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.company(),
                        String.valueOf(row.currentStock()),
                        String.format(Locale.ROOT, "%.1f", row.daysToStockOut()),
                        formatCurrency(row.estimatedRevenueAtRisk())))
                .toList();
    }

    private List<List<String>> buildDeadStockRows() {
        return lastDeadStock.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.company(),
                        String.valueOf(row.daysSinceLastMovement()),
                        formatCurrency(row.deadStockValue())))
                .toList();
    }

    private List<List<String>> buildReturnDamageRows() {
        return lastReturnDamaged.stream()
                .map(row -> List.of(
                        row.medicineName(),
                        row.company(),
                        String.valueOf(row.totalQuantity()),
                        formatCurrency(row.totalValue()),
                        row.rootCauseTags()))
                .toList();
    }

    private void setAiSummaryContent(String content) {
        if (txtAISummary == null) {
            return;
        }
        txtAISummary.getEngine().loadContent(AIHtmlRenderer.toHtmlDocument(content, AIHtmlRenderer.Theme.PANEL));
    }

    private void setSpinnerVisible(boolean visible) {
        if (spinnerSummary == null) {
            return;
        }
        spinnerSummary.setVisible(visible);
        spinnerSummary.setManaged(visible);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null || current.getMessage().isBlank()) {
            return "Unknown error";
        }
        return current.getMessage();
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = dateStart == null ? null : dateStart.getScene();
            if (keyboardShortcutsRegistered || scene == null) {
                return;
            }
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcut);
            keyboardShortcutsRegistered = true;
        });
    }

    private void handleKeyboardShortcut(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.G) {
            handleGenerate();
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.E) {
            handleExportAnalytics();
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
            handleAISummary();
            event.consume();
        }
    }

    private String formatBatchDisplay(int expirySequence, String batchNumber, String batchBarcode) {
        return "#" + expirySequence + " " + safeText(batchNumber) + " | " + safeText(batchBarcode);
    }

    private String formatExpiryDisplay(String expiryDate, Integer daysToExpiry) {
        String safeExpiry = safeText(expiryDate);
        if (daysToExpiry == null) {
            return safeExpiry;
        }
        return safeExpiry + " (" + daysToExpiry + "d)";
    }

    private String formatOverviewExpiry(InventoryBatchDAO.MedicineManagementOverviewRow row) {
        String expiry = safeText(row.earliestBatchExpiry());
        if ("-".equals(expiry)) {
            return expiry;
        }
        return expiry + " | " + row.expiring30dUnits() + " soon / " + row.expiredUnits() + " expired";
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String formatCurrency(double value) {
        return String.format(Locale.ROOT, "₹%.2f", value);
    }

    private String safeLabel(Label label) {
        return label == null || label.getText() == null ? "" : label.getText();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
