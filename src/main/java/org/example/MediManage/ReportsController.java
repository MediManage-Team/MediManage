package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import org.example.MediManage.dao.BillDAO;

import java.time.LocalDate;
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

    private BillDAO billDAO = new BillDAO();

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
    }
}
