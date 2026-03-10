package org.example.MediManage.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.util.AsyncUiFeedback;
import org.example.MediManage.util.ReportingWindowUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

public class ReportsController {
    private static final String AI_SUMMARY_READY_LABEL = "\u2728 Analyze Trends with AI";
    private static final String AI_SUMMARY_BUSY_LABEL = "\u23f3 Analyzing...";

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

    // Data Table
    @FXML private TableView<ItemRow> itemTable;
    @FXML private TableColumn<ItemRow, String> colItemName;
    @FXML private TableColumn<ItemRow, Integer> colItemQty;
    @FXML private TableColumn<ItemRow, String> colItemRevenue;

    // AI
    @FXML private Button btnAISummary;
    @FXML private TextArea txtAISummary;
    @FXML private ProgressIndicator spinnerSummary;

    private final BillDAO billDAO = new BillDAO();
    private final AIAssistantService aiService = new AIAssistantService();

    private Map<String, Double> lastSalesData = new HashMap<>();
    private Map<String, Integer> lastItemVolume = new HashMap<>();
    private double lastTotalRevenue = 0;
    private boolean keyboardShortcutsRegistered = false;

    // Data class for table
    public static class ItemRow {
        private final String name;
        private final int qty;
        private final double revenue;

        public ItemRow(String name, int qty, double revenue) {
            this.name = name;
            this.qty = qty;
            this.revenue = revenue;
        }
        public String getName() { return name; }
        public int getQty() { return qty; }
        public double getRevenue() { return revenue; }
    }

    @FXML
    public void initialize() {
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils
                .currentMondayToSunday(ZoneId.systemDefault());
        dateStart.setValue(weeklyWindow.startDate());
        dateEnd.setValue(weeklyWindow.endDate());

        setupTable();
        loadReport();
        setupKeyboardShortcuts();
    }

    private void setupTable() {
        colItemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colItemQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getQty()).asObject());
        colItemRevenue.setCellValueFactory(d -> new SimpleStringProperty(String.format("₹%.2f", d.getValue().getRevenue())));
    }

    @FXML
    private void handleGenerate() {
        loadReport();
    }

    private void loadReport() {
        LocalDate start = dateStart.getValue();
        LocalDate end = dateEnd.getValue();
        if (start == null || end == null || start.isAfter(end)) return;

        // 1. Fetch raw datasets
        Map<String, Double> salesByDay = billDAO.getSalesBetweenDates(start, end);
        Map<String, Double> profitByDay = billDAO.getProfitBetweenDates(start, end);
        Map<String, Integer> paymentModes = billDAO.getPaymentMethodDistribution(start, end);
        Map<String, Integer> itemVolume = billDAO.getItemizedSales(start, end);
        Map<String, Double> itemRevenue = billDAO.getItemizedRevenue(start, end);

        // 2. Aggregate KPI Totals
        double totalRev = 0;
        for (Double val : salesByDay.values()) totalRev += val;
        
        double totalProf = 0;
        for (Double val : profitByDay.values()) totalProf += val;

        int totalTxns = 0;
        for (Integer count : paymentModes.values()) totalTxns += count;

        double aov = totalTxns > 0 ? (totalRev / totalTxns) : 0.0;
        double avgMargin = totalRev > 0 ? (totalProf / totalRev) * 100 : 0.0;

        // 3. Update KPI Labels
        lblTotalRevenue.setText(String.format("₹%.2f", totalRev));
        lblNetProfit.setText(String.format("₹%.2f", totalProf));
        lblTotalBills.setText(String.valueOf(totalTxns));
        lblAvgOrderValue.setText(String.format("₹%.2f", aov));
        lblAvgMargin.setText(String.format("%.1f%%", avgMargin));

        // 4. Update Line Chart (Sales vs Profit)
        salesChart.getData().clear();
        XYChart.Series<String, Number> seriesRev = new XYChart.Series<>();
        seriesRev.setName("Revenue");
        for (Map.Entry<String, Double> entry : salesByDay.entrySet()) {
            seriesRev.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        XYChart.Series<String, Number> seriesProf = new XYChart.Series<>();
        seriesProf.setName("Net Profit");
        for (Map.Entry<String, Double> entry : profitByDay.entrySet()) {
            seriesProf.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        salesChart.getData().add(seriesRev);
        salesChart.getData().add(seriesProf);

        // 5. Update Pie Chart (Payment Modes)
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Map.Entry<String, Integer> entry : paymentModes.entrySet()) {
            pieData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }
        paymentModesChart.setData(pieData);

        // 6. Update Bar Chart (Top 5 Volume)
        topVolumeChart.getData().clear();
        XYChart.Series<String, Number> seriesVol = new XYChart.Series<>();
        seriesVol.setName("Units Sold");
        int count = 0;
        for (Map.Entry<String, Integer> entry : itemVolume.entrySet()) {
            if (count++ >= 5) break; 
            seriesVol.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        topVolumeChart.getData().add(seriesVol);

        // 7. Update Bar Chart (Top 5 Revenue)
        topRevenueChart.getData().clear();
        XYChart.Series<String, Number> seriesIR = new XYChart.Series<>();
        seriesIR.setName("Revenue Generated");
        count = 0;
        for (Map.Entry<String, Double> entry : itemRevenue.entrySet()) {
            if (count++ >= 5) break;
            seriesIR.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        topRevenueChart.getData().add(seriesIR);

        // 8. Update Data Table (All Items)
        ObservableList<ItemRow> tableRows = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : itemRevenue.entrySet()) {
            String medName = entry.getKey();
            int qty = itemVolume.getOrDefault(medName, 0);
            tableRows.add(new ItemRow(medName, qty, entry.getValue()));
        }
        itemTable.setItems(tableRows);

        // Subtly save data for AI module
        lastSalesData = salesByDay;
        lastItemVolume = itemVolume;
        lastTotalRevenue = totalRev;
    }

    @FXML
    private void handleAISummary() {
        if (lastSalesData.isEmpty()) {
            txtAISummary.setText("No sales data available. Generate a report first.");
            return;
        }
        AsyncUiFeedback.showLoading(btnAISummary, spinnerSummary, txtAISummary,
                AI_SUMMARY_BUSY_LABEL, "\u23f3 Running AI sales analysis...");

        aiService.generateReportSummary(lastSalesData, lastTotalRevenue, lastItemVolume)
                .thenAccept(result -> Platform
                        .runLater(() -> AsyncUiFeedback.showSuccess(btnAISummary, spinnerSummary, txtAISummary,
                                AI_SUMMARY_READY_LABEL, result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> AsyncUiFeedback.showError(btnAISummary, spinnerSummary, txtAISummary,
                            AI_SUMMARY_READY_LABEL, ex));
                    return null;
                });
    }

    // ═══════════════════════════════════════════════
    // KEYBOARD SHORTCUTS
    // ═══════════════════════════════════════════════

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = dateStart == null ? null : dateStart.getScene();
            if (keyboardShortcutsRegistered || scene == null)
                return;
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
        if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
            handleAISummary();
            event.consume();
        }
    }
}
