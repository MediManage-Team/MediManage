package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.util.AsyncUiFeedback;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ReportsController {
    private static final String AI_SUMMARY_READY_LABEL = "✨ Analyze Sales with AI";
    private static final String AI_SUMMARY_BUSY_LABEL = "⏳ Analyzing...";
    private static final String NO_TOP_ITEMS_LABEL = "No itemized sales in selected range.";

    @FXML
    private DatePicker dateStart;
    @FXML
    private DatePicker dateEnd;
    @FXML
    private LineChart<String, Number> salesChart;
    @FXML
    private CategoryAxis xAxis;
    @FXML
    private NumberAxis yAxis;
    @FXML
    private Label lblTotalRevenue;
    @FXML
    private Label lblTopCategories;
    @FXML
    private Button btnAISummary;
    @FXML
    private TextArea txtAISummary;
    @FXML
    private ProgressIndicator spinnerSummary;

    private BillDAO billDAO = new BillDAO();
    private AIAssistantService aiService = new AIAssistantService();
    private Map<String, Double> lastSalesData = new HashMap<>();
    private double lastTotalRevenue = 0;
    private boolean keyboardShortcutsRegistered = false;

    @FXML
    public void initialize() {
        // Defaults: Last 7 days
        dateStart.setValue(LocalDate.now().minusDays(7));
        dateEnd.setValue(LocalDate.now());

        loadReport();
        setupKeyboardShortcuts();
    }

    @FXML
    private void handleGenerate() {
        loadReport();
    }

    private void loadReport() {
        LocalDate start = dateStart.getValue();
        LocalDate end = dateEnd.getValue();

        if (start == null || end == null || start.isAfter(end)) {
            return;
        }

        // Fetch Data
        Map<String, Double> data = billDAO.getSalesBetweenDates(start, end);

        // Update Chart
        salesChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Revenue");

        double total = 0;

        for (Map.Entry<String, Double> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            total += entry.getValue();
        }

        salesChart.getData().add(series);

        // Update Summary
        lblTotalRevenue.setText(String.format("₹ %.2f", total));
        if (lblTopCategories != null) {
            lblTopCategories.setText(formatTopItemsSummary(billDAO.getItemizedSales(start, end)));
        }

        // Store for AI analysis
        lastSalesData = data;
        lastTotalRevenue = total;
    }

    private String formatTopItemsSummary(Map<String, Integer> itemizedSales) {
        if (itemizedSales == null || itemizedSales.isEmpty()) {
            return NO_TOP_ITEMS_LABEL;
        }
        StringBuilder summary = new StringBuilder();
        int added = 0;
        for (Map.Entry<String, Integer> entry : itemizedSales.entrySet()) {
            if (added >= 3) {
                break;
            }
            if (summary.length() > 0) {
                summary.append(" • ");
            }
            String itemName = entry.getKey() == null || entry.getKey().isBlank() ? "Unknown Item" : entry.getKey();
            summary.append(itemName).append(" (").append(entry.getValue()).append(")");
            added++;
        }
        return summary.isEmpty() ? NO_TOP_ITEMS_LABEL : summary.toString();
    }

    @FXML
    private void handleAISummary() {
        if (lastSalesData.isEmpty()) {
            txtAISummary.setText("No sales data available. Generate a report first.");
            return;
        }

        AsyncUiFeedback.showLoading(btnAISummary, spinnerSummary, txtAISummary,
                AI_SUMMARY_BUSY_LABEL, "⏳ Running AI sales analysis...");

        aiService.generateReportSummary(lastSalesData, lastTotalRevenue)
                .thenAccept(result -> Platform.runLater(() -> {
                    AsyncUiFeedback.showSuccess(btnAISummary, spinnerSummary, txtAISummary,
                            AI_SUMMARY_READY_LABEL, result);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        AsyncUiFeedback.showError(btnAISummary, spinnerSummary, txtAISummary,
                                AI_SUMMARY_READY_LABEL, ex);
                    });
                    return null;
                });
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

        if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
            handleAISummary();
            event.consume();
        }
    }
}
