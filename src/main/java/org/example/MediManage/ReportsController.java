package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.application.Platform;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.service.ai.AIAssistantService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ReportsController {

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
    private Button btnAISummary;
    @FXML
    private TextArea txtAISummary;
    @FXML
    private ProgressIndicator spinnerSummary;

    private BillDAO billDAO = new BillDAO();
    private AIAssistantService aiService = new AIAssistantService();
    private Map<String, Double> lastSalesData = new HashMap<>();
    private double lastTotalRevenue = 0;

    @FXML
    public void initialize() {
        // Defaults: Last 7 days
        dateStart.setValue(LocalDate.now().minusDays(7));
        dateEnd.setValue(LocalDate.now());

        loadReport();
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

        // Store for AI analysis
        lastSalesData = data;
        lastTotalRevenue = total;
    }

    @FXML
    private void handleAISummary() {
        if (lastSalesData.isEmpty()) {
            txtAISummary.setText("No sales data available. Generate a report first.");
            return;
        }

        txtAISummary.setText("Analyzing sales trends with AI...");
        btnAISummary.setDisable(true);
        if (spinnerSummary != null) {
            spinnerSummary.setVisible(true);
            spinnerSummary.setManaged(true);
        }

        aiService.generateReportSummary(lastSalesData, lastTotalRevenue)
                .thenAccept(result -> Platform.runLater(() -> {
                    txtAISummary.setText(result);
                    btnAISummary.setDisable(false);
                    if (spinnerSummary != null) {
                        spinnerSummary.setVisible(false);
                        spinnerSummary.setManaged(false);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        txtAISummary.setText("Error: " + ex.getMessage());
                        btnAISummary.setDisable(false);
                        if (spinnerSummary != null) {
                            spinnerSummary.setVisible(false);
                            spinnerSummary.setManaged(false);
                        }
                    });
                    return null;
                });
    }
}
