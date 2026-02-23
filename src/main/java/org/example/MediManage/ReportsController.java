package org.example.MediManage;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
import org.example.MediManage.config.FeatureFlag;
import org.example.MediManage.config.FeatureFlags;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.User;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.service.subscription.SubscriptionModelMonitoringService;
import org.example.MediManage.util.AsyncUiFeedback;
import org.example.MediManage.util.ReportingWindowUtils;
import org.example.MediManage.util.UserSession;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

public class ReportsController {
    private static final String AI_SUMMARY_READY_LABEL = "✨ Analyze Sales with AI";
    private static final String AI_SUMMARY_BUSY_LABEL = "⏳ Analyzing...";
    private static final String NO_TOP_ITEMS_LABEL = "No itemized sales in selected range.";
    private static final String NO_SUBSCRIPTION_DATA_LABEL = "No subscription report data in selected range.";
    private static final String NO_REJECTED_OVERRIDES_LABEL = "No rejected override attempts in selected range.";
    private static final String NO_PRICING_ALERTS_LABEL = "No pricing integrity alerts in selected range.";
    private static final String NO_OVERRIDE_ABUSE_LABEL = "No override abuse signals found for selected range.";
    private static final String NO_PILOT_FEEDBACK_LABEL = "No pilot feedback logged in selected range.";
    private static final String NO_MODEL_MONITORING_LABEL = "No model monitoring data in selected range.";

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
    @FXML
    private TitledPane subscriptionReportsPane;
    @FXML
    private Label lblLeakageSummary;
    @FXML
    private TableView<SubscriptionDAO.PlanRevenueImpactRow> planImpactTable;
    @FXML
    private TableColumn<SubscriptionDAO.PlanRevenueImpactRow, String> colImpactPlan;
    @FXML
    private TableColumn<SubscriptionDAO.PlanRevenueImpactRow, Number> colImpactBills;
    @FXML
    private TableColumn<SubscriptionDAO.PlanRevenueImpactRow, String> colImpactGross;
    @FXML
    private TableColumn<SubscriptionDAO.PlanRevenueImpactRow, String> colImpactSavings;
    @FXML
    private TableColumn<SubscriptionDAO.PlanRevenueImpactRow, String> colImpactNet;
    @FXML
    private TableColumn<SubscriptionDAO.PlanRevenueImpactRow, String> colImpactLeakage;
    @FXML
    private TableView<SubscriptionDAO.DiscountLeakageRow> leakageTable;
    @FXML
    private TableColumn<SubscriptionDAO.DiscountLeakageRow, String> colLeakageDay;
    @FXML
    private TableColumn<SubscriptionDAO.DiscountLeakageRow, Number> colLeakageBills;
    @FXML
    private TableColumn<SubscriptionDAO.DiscountLeakageRow, String> colLeakageGross;
    @FXML
    private TableColumn<SubscriptionDAO.DiscountLeakageRow, String> colLeakageSavings;
    @FXML
    private TableColumn<SubscriptionDAO.DiscountLeakageRow, String> colLeakagePercent;
    @FXML
    private Label lblRejectedSummary;
    @FXML
    private TableView<SubscriptionDAO.RejectedOverrideReportRow> rejectedOverridesTable;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, Number> colRejectedOverrideId;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, Number> colRejectedCustomer;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, Number> colRejectedEnrollment;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, String> colRejectedRequestedPercent;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, String> colRejectedRequestedBy;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, String> colRejectedRejectedBy;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, String> colRejectedAt;
    @FXML
    private TableColumn<SubscriptionDAO.RejectedOverrideReportRow, String> colRejectedReason;
    @FXML
    private Label lblPilotMonitoringSummary;
    @FXML
    private Label lblPilotGateStatus;
    @FXML
    private Label lblPricingAlertsSummary;
    @FXML
    private TableView<SubscriptionDAO.PricingIntegrityAlertRow> pricingAlertsTable;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, Number> colPricingBillId;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingBillDate;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingPlan;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingNet;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingSavings;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingConfiguredPercent;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingComputedPercent;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingAlertCode;
    @FXML
    private TableColumn<SubscriptionDAO.PricingIntegrityAlertRow, String> colPricingSeverity;
    @FXML
    private Label lblOverrideAbuseSummary;
    @FXML
    private TableView<SubscriptionDAO.OverrideAbuseSignalRow> overrideAbuseTable;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, String> colAbuseRequester;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, Number> colAbuseRequests;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, Number> colAbuseApproved;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, Number> colAbuseRejected;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, Number> colAbusePending;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, String> colAbuseAverageRequested;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, String> colAbuseRejectionRate;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, String> colAbuseSeverity;
    @FXML
    private TableColumn<SubscriptionDAO.OverrideAbuseSignalRow, String> colAbuseLatest;
    @FXML
    private ComboBox<String> cmbFeedbackSeverity;
    @FXML
    private TextField txtFeedbackTitle;
    @FXML
    private TextArea txtFeedbackDetails;
    @FXML
    private TextField txtFeedbackOwnerUserId;
    @FXML
    private TextField txtFeedbackLinkedBillId;
    @FXML
    private TextField txtFeedbackLinkedOverrideId;
    @FXML
    private Label lblPilotFeedbackSummary;
    @FXML
    private TableView<SubscriptionDAO.PilotFeedbackRow> pilotFeedbackTable;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, Number> colFeedbackId;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, String> colFeedbackReportedAt;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, String> colFeedbackSeverity;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, String> colFeedbackStatus;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, String> colFeedbackTitle;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, String> colFeedbackReportedBy;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, String> colFeedbackOwner;
    @FXML
    private TableColumn<SubscriptionDAO.PilotFeedbackRow, String> colFeedbackResolvedAt;
    @FXML
    private Label lblModelMonitoringSummary;
    @FXML
    private Label lblAbuseDetectionMetrics;
    @FXML
    private Label lblRecommendationAcceptanceMetrics;

    private final BillDAO billDAO = new BillDAO();
    private final SubscriptionDAO subscriptionDAO = new SubscriptionDAO();
    private final AIAssistantService aiService = new AIAssistantService();
    private final SubscriptionModelMonitoringService modelMonitoringService =
            new SubscriptionModelMonitoringService(subscriptionDAO);
    private Map<String, Double> lastSalesData = new HashMap<>();
    private double lastTotalRevenue = 0;
    private boolean keyboardShortcutsRegistered = false;
    private boolean subscriptionReportsEnabled;

    @FXML
    public void initialize() {
        ReportingWindowUtils.WeeklyWindow weeklyWindow =
                ReportingWindowUtils.currentMondayToSunday(ZoneId.systemDefault());
        dateStart.setValue(weeklyWindow.startDate());
        dateEnd.setValue(weeklyWindow.endDate());

        configureSubscriptionTables();
        configurePilotFeedbackInputs();
        configureSubscriptionSectionVisibility();
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
        loadSubscriptionReports(start, end);

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

    private void configureSubscriptionSectionVisibility() {
        subscriptionReportsEnabled = FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE);
        if (subscriptionReportsPane != null) {
            subscriptionReportsPane.setManaged(subscriptionReportsEnabled);
            subscriptionReportsPane.setVisible(subscriptionReportsEnabled);
        }
    }

    private void configureSubscriptionTables() {
        if (planImpactTable != null) {
            colImpactPlan.setCellValueFactory(cd -> new SimpleStringProperty(
                    (cd.getValue().planCode() == null ? "" : cd.getValue().planCode())
                            + " - "
                            + (cd.getValue().planName() == null ? "Unknown" : cd.getValue().planName())));
            colImpactBills.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().billCount()));
            colImpactGross.setCellValueFactory(cd -> new SimpleStringProperty(formatCurrency(cd.getValue().grossAmountBeforeDiscount())));
            colImpactSavings.setCellValueFactory(cd -> new SimpleStringProperty(formatCurrency(cd.getValue().totalSavings())));
            colImpactNet.setCellValueFactory(cd -> new SimpleStringProperty(formatCurrency(cd.getValue().netBilledAmount())));
            colImpactLeakage.setCellValueFactory(cd -> new SimpleStringProperty(formatPercent(cd.getValue().leakagePercent())));
            planImpactTable.setItems(FXCollections.observableArrayList());
        }

        if (leakageTable != null) {
            colLeakageDay.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().billDay()));
            colLeakageBills.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().billCount()));
            colLeakageGross.setCellValueFactory(cd -> new SimpleStringProperty(formatCurrency(cd.getValue().grossAmountBeforeDiscount())));
            colLeakageSavings.setCellValueFactory(cd -> new SimpleStringProperty(formatCurrency(cd.getValue().totalSavings())));
            colLeakagePercent.setCellValueFactory(cd -> new SimpleStringProperty(formatPercent(cd.getValue().leakagePercent())));
            leakageTable.setItems(FXCollections.observableArrayList());
        }

        if (rejectedOverridesTable != null) {
            colRejectedOverrideId.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().overrideId()));
            colRejectedCustomer.setCellValueFactory(cd -> new SimpleLongProperty(
                    cd.getValue().customerId() == null ? 0L : cd.getValue().customerId()));
            colRejectedEnrollment.setCellValueFactory(cd -> new SimpleLongProperty(
                    cd.getValue().enrollmentId() == null ? 0L : cd.getValue().enrollmentId()));
            colRejectedRequestedPercent.setCellValueFactory(cd -> new SimpleStringProperty(
                    formatPercent(cd.getValue().requestedDiscountPercent())));
            colRejectedRequestedBy.setCellValueFactory(cd -> new SimpleStringProperty(
                    formatActor(cd.getValue().requestedByUsername(), cd.getValue().requestedByUserId())));
            colRejectedRejectedBy.setCellValueFactory(cd -> new SimpleStringProperty(
                    formatActor(cd.getValue().rejectedByUsername(), cd.getValue().rejectedByUserId())));
            colRejectedAt.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().rejectedAt() == null ? "" : cd.getValue().rejectedAt()));
            colRejectedReason.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().requestReason() == null ? "" : cd.getValue().requestReason()));
            rejectedOverridesTable.setItems(FXCollections.observableArrayList());
        }

        if (pricingAlertsTable != null) {
            colPricingBillId.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().billId()));
            colPricingBillDate.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().billDate() == null ? "" : cd.getValue().billDate()));
            colPricingPlan.setCellValueFactory(cd -> new SimpleStringProperty(
                    (cd.getValue().planCode() == null ? "PLAN-" + cd.getValue().planId() : cd.getValue().planCode())
                            + " - "
                            + (cd.getValue().planName() == null ? "Unknown" : cd.getValue().planName())));
            colPricingNet.setCellValueFactory(cd -> new SimpleStringProperty(formatCurrency(cd.getValue().netBilledAmount())));
            colPricingSavings.setCellValueFactory(cd -> new SimpleStringProperty(formatCurrency(cd.getValue().savingsAmount())));
            colPricingConfiguredPercent.setCellValueFactory(
                    cd -> new SimpleStringProperty(formatPercent(cd.getValue().configuredDiscountPercent())));
            colPricingComputedPercent.setCellValueFactory(
                    cd -> new SimpleStringProperty(formatPercent(cd.getValue().computedDiscountPercent())));
            colPricingAlertCode.setCellValueFactory(cd -> new SimpleStringProperty(formatAlertCode(cd.getValue().alertCode())));
            colPricingSeverity.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().severity() == null ? "" : cd.getValue().severity()));
            pricingAlertsTable.setItems(FXCollections.observableArrayList());
        }

        if (overrideAbuseTable != null) {
            colAbuseRequester.setCellValueFactory(cd -> new SimpleStringProperty(
                    formatActor(cd.getValue().requestedByUsername(), cd.getValue().requestedByUserId())));
            colAbuseRequests.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().totalRequests()));
            colAbuseApproved.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().approvedCount()));
            colAbuseRejected.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().rejectedCount()));
            colAbusePending.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().pendingCount()));
            colAbuseAverageRequested.setCellValueFactory(
                    cd -> new SimpleStringProperty(formatPercent(cd.getValue().averageRequestedPercent())));
            colAbuseRejectionRate.setCellValueFactory(
                    cd -> new SimpleStringProperty(formatPercent(cd.getValue().rejectionRatePercent())));
            colAbuseSeverity.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().severity() == null ? "" : cd.getValue().severity()));
            colAbuseLatest.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().latestRequestAt() == null ? "" : cd.getValue().latestRequestAt()));
            overrideAbuseTable.setItems(FXCollections.observableArrayList());
        }

        if (pilotFeedbackTable != null) {
            colFeedbackId.setCellValueFactory(cd -> new SimpleLongProperty(cd.getValue().feedbackId()));
            colFeedbackReportedAt.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().reportedAt() == null ? "" : cd.getValue().reportedAt()));
            colFeedbackSeverity.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().severity() == null ? "" : cd.getValue().severity()));
            colFeedbackStatus.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().status() == null ? "" : cd.getValue().status()));
            colFeedbackTitle.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().title() == null ? "" : cd.getValue().title()));
            colFeedbackReportedBy.setCellValueFactory(cd -> new SimpleStringProperty(
                    formatActor(cd.getValue().reportedByUsername(), cd.getValue().reportedByUserId())));
            colFeedbackOwner.setCellValueFactory(cd -> new SimpleStringProperty(
                    formatActor(cd.getValue().ownerUsername(), cd.getValue().ownerUserId())));
            colFeedbackResolvedAt.setCellValueFactory(cd -> new SimpleStringProperty(
                    cd.getValue().resolvedAt() == null ? "" : cd.getValue().resolvedAt()));
            pilotFeedbackTable.setItems(FXCollections.observableArrayList());
        }
    }

    private void configurePilotFeedbackInputs() {
        if (cmbFeedbackSeverity != null) {
            cmbFeedbackSeverity.setItems(FXCollections.observableArrayList("LOW", "MEDIUM", "HIGH", "CRITICAL"));
            cmbFeedbackSeverity.setValue("MEDIUM");
        }
    }

    private void loadSubscriptionReports(LocalDate start, LocalDate end) {
        if (!subscriptionReportsEnabled) {
            return;
        }

        subscriptionDAO.refreshWeeklyAnalyticsSummary(end, ZoneId.systemDefault().getId());
        var planRows = subscriptionDAO.getPlanRevenueImpact(start, end);
        var leakageRows = subscriptionDAO.getDiscountLeakageByDay(start, end);
        var rejectedRows = subscriptionDAO.getRejectedOverrideAttempts(start, end, 200);
        var pricingAlerts = subscriptionDAO.getPricingIntegrityAlerts(start, end, 200);
        var overrideAbuseRows = subscriptionDAO.getOverrideAbuseSignals(start, end, 3);
        var pilotFeedbackRows = subscriptionDAO.getPilotFeedback(start, end, "ALL", 200);
        long highSeverityPricingAlertCount = pricingAlerts.stream()
                .filter(row -> isHighOrCriticalSeverity(row.severity()))
                .count();
        long highSeverityOverrideAbuseCount = overrideAbuseRows.stream()
                .filter(row -> isHighOrCriticalSeverity(row.severity()))
                .count();
        long openHighCriticalFeedbackCount = pilotFeedbackRows.stream()
                .filter(row -> !"RESOLVED".equalsIgnoreCase(row.status()))
                .filter(row -> isHighOrCriticalSeverity(row.severity()))
                .count();
        boolean hasOpenCriticalFeedback = pilotFeedbackRows.stream()
                .anyMatch(row -> !"RESOLVED".equalsIgnoreCase(row.status())
                        && "CRITICAL".equalsIgnoreCase(row.severity()));

        if (planImpactTable != null) {
            planImpactTable.setItems(FXCollections.observableArrayList(planRows));
        }
        if (leakageTable != null) {
            leakageTable.setItems(FXCollections.observableArrayList(leakageRows));
        }
        if (rejectedOverridesTable != null) {
            rejectedOverridesTable.setItems(FXCollections.observableArrayList(rejectedRows));
        }
        if (pricingAlertsTable != null) {
            pricingAlertsTable.setItems(FXCollections.observableArrayList(pricingAlerts));
        }
        if (overrideAbuseTable != null) {
            overrideAbuseTable.setItems(FXCollections.observableArrayList(overrideAbuseRows));
        }
        if (pilotFeedbackTable != null) {
            pilotFeedbackTable.setItems(FXCollections.observableArrayList(pilotFeedbackRows));
        }

        if (lblLeakageSummary != null) {
            if (planRows.isEmpty()) {
                lblLeakageSummary.setText(NO_SUBSCRIPTION_DATA_LABEL);
            } else {
                long totalBills = 0L;
                double totalGross = 0.0;
                double totalSavings = 0.0;
                for (SubscriptionDAO.PlanRevenueImpactRow row : planRows) {
                    totalBills += row.billCount();
                    totalGross += row.grossAmountBeforeDiscount();
                    totalSavings += row.totalSavings();
                }
                totalGross = round2(totalGross);
                totalSavings = round2(totalSavings);
                double leakagePercent = totalGross <= 0.0 ? 0.0 : round4((totalSavings / totalGross) * 100.0);

                lblLeakageSummary.setText("Bills: " + totalBills
                        + " | Gross (before discount): " + formatCurrency(totalGross)
                        + " | Savings: " + formatCurrency(totalSavings)
                        + " | Leakage: " + formatPercent(leakagePercent));
            }
        }

        if (lblRejectedSummary != null) {
            if (rejectedRows.isEmpty()) {
                lblRejectedSummary.setText(NO_REJECTED_OVERRIDES_LABEL);
            } else {
                double avgRequestedPercent = rejectedRows.stream()
                        .mapToDouble(SubscriptionDAO.RejectedOverrideReportRow::requestedDiscountPercent)
                        .average()
                        .orElse(0.0);
                String latestRejectedAt = rejectedRows.get(0).rejectedAt() == null
                        ? rejectedRows.get(0).requestedAt()
                        : rejectedRows.get(0).rejectedAt();
                lblRejectedSummary.setText("Rejected overrides: " + rejectedRows.size()
                        + " | Avg requested discount: " + formatPercent(round4(avgRequestedPercent))
                        + " | Latest rejection: " + (latestRejectedAt == null ? "-" : latestRejectedAt));
            }
        }

        if (lblPricingAlertsSummary != null) {
            if (pricingAlerts.isEmpty()) {
                lblPricingAlertsSummary.setText(NO_PRICING_ALERTS_LABEL);
            } else {
                lblPricingAlertsSummary.setText("Pricing alerts: " + pricingAlerts.size()
                        + " | High severity: " + highSeverityPricingAlertCount);
            }
        }

        if (lblOverrideAbuseSummary != null) {
            if (overrideAbuseRows.isEmpty()) {
                lblOverrideAbuseSummary.setText(NO_OVERRIDE_ABUSE_LABEL);
            } else {
                SubscriptionDAO.OverrideAbuseSignalRow top = overrideAbuseRows.get(0);
                lblOverrideAbuseSummary.setText("Override abuse signals: " + overrideAbuseRows.size()
                        + " | High severity: " + highSeverityOverrideAbuseCount
                        + " | Top requester: " + formatActor(top.requestedByUsername(), top.requestedByUserId()));
            }
        }

        if (lblPilotMonitoringSummary != null) {
            lblPilotMonitoringSummary.setText("Pilot monitors -> Pricing alerts: " + pricingAlerts.size()
                    + " | Override abuse signals: " + overrideAbuseRows.size());
        }
        updatePilotGateStatus(
                highSeverityPricingAlertCount,
                highSeverityOverrideAbuseCount,
                openHighCriticalFeedbackCount,
                hasOpenCriticalFeedback);

        if (lblPilotFeedbackSummary != null) {
            if (pilotFeedbackRows.isEmpty()) {
                lblPilotFeedbackSummary.setText(NO_PILOT_FEEDBACK_LABEL);
            } else {
                long openCount = pilotFeedbackRows.stream()
                        .filter(row -> "OPEN".equalsIgnoreCase(row.status()))
                        .count();
                long inProgressCount = pilotFeedbackRows.stream()
                        .filter(row -> "IN_PROGRESS".equalsIgnoreCase(row.status()))
                        .count();
                long resolvedCount = pilotFeedbackRows.stream()
                        .filter(row -> "RESOLVED".equalsIgnoreCase(row.status()))
                        .count();
                long highSeverityCount = pilotFeedbackRows.stream()
                        .filter(row -> "HIGH".equalsIgnoreCase(row.severity())
                                || "CRITICAL".equalsIgnoreCase(row.severity()))
                        .count();
                lblPilotFeedbackSummary.setText("Pilot feedback items: " + pilotFeedbackRows.size()
                        + " | Open: " + openCount
                        + " | In progress: " + inProgressCount
                        + " | Resolved: " + resolvedCount
                        + " | High/Critical: " + highSeverityCount);
            }
        }

        loadModelMonitoringSummary(start, end);
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

    @FXML
    private void handleLogPilotFeedback() {
        User currentUser = UserSession.getInstance().getUser();
        if (currentUser == null) {
            showAlert(Alert.AlertType.ERROR, "Not Logged In", "Please sign in again to log pilot feedback.");
            return;
        }

        String title = txtFeedbackTitle == null ? null : txtFeedbackTitle.getText();
        if (title == null || title.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Feedback title is required.");
            return;
        }

        String severity = cmbFeedbackSeverity == null || cmbFeedbackSeverity.getValue() == null
                ? "MEDIUM"
                : cmbFeedbackSeverity.getValue();
        String details = txtFeedbackDetails == null ? null : txtFeedbackDetails.getText();
        Integer ownerUserId = parseOptionalInt(txtFeedbackOwnerUserId == null ? null : txtFeedbackOwnerUserId.getText());
        Integer linkedBillId = parseOptionalInt(txtFeedbackLinkedBillId == null ? null : txtFeedbackLinkedBillId.getText());
        Integer linkedOverrideId = parseOptionalInt(
                txtFeedbackLinkedOverrideId == null ? null : txtFeedbackLinkedOverrideId.getText());

        try {
            int feedbackId = subscriptionDAO.createPilotFeedback(
                    "PILOT",
                    severity,
                    title,
                    details,
                    currentUser.getId(),
                    ownerUserId,
                    linkedBillId,
                    linkedOverrideId);
            clearPilotFeedbackInputs();
            loadReport();
            showAlert(Alert.AlertType.INFORMATION, "Pilot Feedback Logged", "Feedback #" + feedbackId + " captured.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Save Failed", e.getMessage());
        }
    }

    @FXML
    private void handleMarkFeedbackInProgress() {
        updateSelectedFeedbackStatus("IN_PROGRESS", false);
    }

    @FXML
    private void handleMarkFeedbackResolved() {
        updateSelectedFeedbackStatus("RESOLVED", true);
    }

    @FXML
    private void handleRefreshPilotFeedback() {
        loadReport();
    }

    private void updateSelectedFeedbackStatus(String targetStatus, boolean requireResolutionNotes) {
        if (pilotFeedbackTable == null) {
            return;
        }
        SubscriptionDAO.PilotFeedbackRow selected = pilotFeedbackTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select a feedback row first.");
            return;
        }

        User currentUser = UserSession.getInstance().getUser();
        if (currentUser == null) {
            showAlert(Alert.AlertType.ERROR, "Not Logged In", "Please sign in again to update feedback.");
            return;
        }

        String resolutionNotes = null;
        if (requireResolutionNotes) {
            TextInputDialog notesDialog = new TextInputDialog();
            notesDialog.setTitle("Resolution Notes");
            notesDialog.setHeaderText("Feedback #" + selected.feedbackId());
            notesDialog.setContentText("Resolution notes:");
            var response = notesDialog.showAndWait();
            if (response.isEmpty() || response.get().trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Resolution Required", "Resolution notes are required.");
                return;
            }
            resolutionNotes = response.get().trim();
        }

        try {
            subscriptionDAO.updatePilotFeedbackStatus(
                    selected.feedbackId(),
                    targetStatus,
                    currentUser.getId(),
                    resolutionNotes);
            loadReport();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Update Failed", e.getMessage());
        }
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

    private String formatCurrency(double value) {
        return String.format("₹ %.2f", value);
    }

    private void loadModelMonitoringSummary(LocalDate start, LocalDate end) {
        if (lblModelMonitoringSummary == null || lblAbuseDetectionMetrics == null || lblRecommendationAcceptanceMetrics == null) {
            return;
        }
        try {
            SubscriptionModelMonitoringService.MonitoringSnapshot snapshot = modelMonitoringService.evaluate(start, end);
            SubscriptionModelMonitoringService.AbuseDetectionMonitoring abuse = snapshot.abuseDetection();
            SubscriptionModelMonitoringService.RecommendationMonitoring recommendation = snapshot.recommendation();

            if (abuse.predictedPositiveCount() <= 0
                    && abuse.actualPositiveCount() <= 0
                    && recommendation.totalEnrollments() <= 0) {
                lblModelMonitoringSummary.setText(NO_MODEL_MONITORING_LABEL);
                lblAbuseDetectionMetrics.setText("Abuse precision: 0.00% | recall: 0.00% | TP: 0 | FP: 0 | FN: 0");
                lblRecommendationAcceptanceMetrics
                        .setText("Recommendation acceptance: 0.00% | Accepted: 0/0 | Enrollments in window: 0");
                return;
            }

            lblModelMonitoringSummary.setText("Model monitoring window: " + start + " to " + end
                    + " | Abuse labels sourced from resolved high-severity pilot feedback.");
            lblAbuseDetectionMetrics.setText("Abuse precision: " + formatPercent(abuse.precisionPercent())
                    + " | recall: " + formatPercent(abuse.recallPercent())
                    + " | TP: " + abuse.truePositiveCount()
                    + " | FP: " + abuse.falsePositiveCount()
                    + " | FN: " + abuse.falseNegativeCount());
            lblRecommendationAcceptanceMetrics.setText("Recommendation acceptance: "
                    + formatPercent(recommendation.acceptanceRatePercent())
                    + " | Accepted: " + recommendation.acceptedCount() + "/" + recommendation.evaluatedCount()
                    + " | Enrollments in window: " + recommendation.totalEnrollments()
                    + (recommendation.skippedCount() > 0 ? " | Skipped (no recommendation): " + recommendation.skippedCount() : ""));
        } catch (Exception e) {
            lblModelMonitoringSummary.setText(NO_MODEL_MONITORING_LABEL);
            lblAbuseDetectionMetrics.setText("Abuse precision/recall unavailable.");
            lblRecommendationAcceptanceMetrics.setText("Recommendation acceptance unavailable.");
        }
    }

    private void updatePilotGateStatus(
            long highSeverityPricingAlertCount,
            long highSeverityOverrideAbuseCount,
            long openHighCriticalFeedbackCount,
            boolean hasOpenCriticalFeedback) {
        if (lblPilotGateStatus == null) {
            return;
        }

        long totalBlockingSignals = highSeverityPricingAlertCount
                + highSeverityOverrideAbuseCount
                + openHighCriticalFeedbackCount;
        String gateText;
        String styleClass;
        if (totalBlockingSignals == 0) {
            gateText = "Pilot gate: PASS | No high-severity blockers.";
            styleClass = "text-success";
        } else {
            gateText = "Pilot gate: HOLD | High pricing alerts: " + highSeverityPricingAlertCount
                    + " | High override signals: " + highSeverityOverrideAbuseCount
                    + " | Open high/critical feedback: " + openHighCriticalFeedbackCount;
            styleClass = hasOpenCriticalFeedback ? "text-danger" : "text-warning";
        }

        lblPilotGateStatus.setText(gateText);
        lblPilotGateStatus.getStyleClass().removeAll("text-muted", "text-success", "text-warning", "text-danger");
        if (!lblPilotGateStatus.getStyleClass().contains("label-strong")) {
            lblPilotGateStatus.getStyleClass().add("label-strong");
        }
        if (!lblPilotGateStatus.getStyleClass().contains(styleClass)) {
            lblPilotGateStatus.getStyleClass().add(styleClass);
        }
    }

    private boolean isHighOrCriticalSeverity(String severity) {
        return "HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity);
    }

    private String formatPercent(double value) {
        return String.format("%.2f%%", value);
    }

    private String formatActor(String username, Integer userId) {
        String safeUser = username == null || username.isBlank() ? "user" : username.trim();
        if (userId == null || userId <= 0) {
            return safeUser;
        }
        return safeUser + " (#" + userId + ")";
    }

    private String formatAlertCode(String alertCode) {
        if (alertCode == null || alertCode.isBlank()) {
            return "";
        }
        String normalized = alertCode.trim().replace('_', ' ').toLowerCase();
        StringBuilder formatted = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isWhitespace(ch)) {
                formatted.append(ch);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                formatted.append(Character.toUpperCase(ch));
                capitalizeNext = false;
            } else {
                formatted.append(ch);
            }
        }
        return formatted.toString();
    }

    private void clearPilotFeedbackInputs() {
        if (txtFeedbackTitle != null) {
            txtFeedbackTitle.clear();
        }
        if (txtFeedbackDetails != null) {
            txtFeedbackDetails.clear();
        }
        if (txtFeedbackOwnerUserId != null) {
            txtFeedbackOwnerUserId.clear();
        }
        if (txtFeedbackLinkedBillId != null) {
            txtFeedbackLinkedBillId.clear();
        }
        if (txtFeedbackLinkedOverrideId != null) {
            txtFeedbackLinkedOverrideId.clear();
        }
        if (cmbFeedbackSeverity != null) {
            cmbFeedbackSeverity.setValue("MEDIUM");
        }
    }

    private Integer parseOptionalInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Numeric fields must be valid integers.");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
