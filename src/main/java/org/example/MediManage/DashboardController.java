package org.example.MediManage;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.MediManage.dao.AnalyticsReportDispatchDAO;
import org.example.MediManage.dao.AnomalyActionTrackerDAO;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.BillHistoryRecord;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.UserRole;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;

import org.example.MediManage.service.ReportService;
import org.example.MediManage.service.DashboardKpiService;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import org.example.MediManage.model.Expense;
import javafx.scene.layout.GridPane;
import javafx.application.Platform;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.ReportingWindowUtils;
import org.example.MediManage.util.UserSession;
import org.example.MediManage.util.WeeklyAnomalyAlertEvaluator;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Refactored DashboardController — focused on KPIs, inventory overview,
 * bill history, business intelligence (AI), and expenses.
 *
 * Billing and customer management are handled by dedicated
 * BillingController and CustomersController.
 */
public class DashboardController {

    private final ReportService reportService = new ReportService();
    private UserSession userSession;

    // KPI Labels
    @FXML
    private Label dailySales;
    @FXML
    private Label totalProfit;
    @FXML
    private Label pendingRx;
    @FXML
    private Label lowStock;
    @FXML
    private Label activeSubscribers;
    @FXML
    private Label renewalsDue;
    @FXML
    private Label subscriptionDiscountValue;
    @FXML
    private Label pendingOverrides;
    @FXML
    private Label lblExpiryWeekWindow;
    @FXML
    private Label lblExpiryExpired;
    @FXML
    private Label lblExpiry0To30;
    @FXML
    private Label lblExpiry31To60;
    @FXML
    private Label lblExpiry61To90;
    @FXML
    private Label lblOutOfStockWindow;
    @FXML
    private Label lblOutOfStockSkuCount;
    @FXML
    private Label lblOutOfStockAvgDays;
    @FXML
    private Label lblOutOfStockRevenueImpact;
    @FXML
    private TableView<MedicineDAO.OutOfStockInsightRow> outOfStockTable;
    @FXML
    private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockMedicine;
    @FXML
    private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockCompany;
    @FXML
    private TableColumn<MedicineDAO.OutOfStockInsightRow, Number> colOutOfStockDays;
    @FXML
    private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockLastSale;
    @FXML
    private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockAvgDailyRevenue;
    @FXML
    private TableColumn<MedicineDAO.OutOfStockInsightRow, String> colOutOfStockRevenueImpact;
    @FXML
    private Label lblNearStockOutWindow;
    @FXML
    private Label lblNearStockOutSkuCount;
    @FXML
    private Label lblNearStockOutAvgDaysLeft;
    @FXML
    private Label lblNearStockOutRevenueAtRisk;
    @FXML
    private TableView<MedicineDAO.NearStockOutInsightRow> nearStockOutTable;
    @FXML
    private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutMedicine;
    @FXML
    private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutCompany;
    @FXML
    private TableColumn<MedicineDAO.NearStockOutInsightRow, Number> colNearStockOutStock;
    @FXML
    private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutAvgDailyConsumption;
    @FXML
    private TableColumn<MedicineDAO.NearStockOutInsightRow, Number> colNearStockOutThreshold;
    @FXML
    private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutDaysLeft;
    @FXML
    private TableColumn<MedicineDAO.NearStockOutInsightRow, String> colNearStockOutRevenueAtRisk;
    @FXML
    private Label lblDeadStockWindow;
    @FXML
    private Label lblDeadStockSkuCount;
    @FXML
    private Label lblDeadStockAvgDaysStagnant;
    @FXML
    private Label lblDeadStockValue;
    @FXML
    private TableView<MedicineDAO.DeadStockInsightRow> deadStockTable;
    @FXML
    private TableColumn<MedicineDAO.DeadStockInsightRow, String> colDeadStockMedicine;
    @FXML
    private TableColumn<MedicineDAO.DeadStockInsightRow, String> colDeadStockCompany;
    @FXML
    private TableColumn<MedicineDAO.DeadStockInsightRow, Number> colDeadStockStock;
    @FXML
    private TableColumn<MedicineDAO.DeadStockInsightRow, String> colDeadStockLastSale;
    @FXML
    private TableColumn<MedicineDAO.DeadStockInsightRow, Number> colDeadStockDaysStagnant;
    @FXML
    private TableColumn<MedicineDAO.DeadStockInsightRow, String> colDeadStockValue;
    @FXML
    private Label lblFastMovingWindow;
    @FXML
    private Label lblFastMovingSkuCount;
    @FXML
    private Label lblFastMovingUnits;
    @FXML
    private Label lblFastMovingRevenue;
    @FXML
    private TableView<MedicineDAO.FastMovingInsightRow> fastMovingTable;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingMedicine;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingCompany;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingUnitsSold;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingRevenue;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingAvgDailyUnits;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingAvgDailyRevenue;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colFastMovingLastSale;
    @FXML
    private Label lblReturnDamagedWindow;
    @FXML
    private Label lblReturnDamagedQuantity;
    @FXML
    private Label lblReturnDamagedValue;
    @FXML
    private Label lblReturnDamagedRootCauses;
    @FXML
    private TableView<MedicineDAO.ReturnDamagedInsightRow> returnDamagedTable;
    @FXML
    private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamagedMedicine;
    @FXML
    private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamagedCompany;
    @FXML
    private TableColumn<MedicineDAO.ReturnDamagedInsightRow, Number> colReturnDamagedReturnedQty;
    @FXML
    private TableColumn<MedicineDAO.ReturnDamagedInsightRow, Number> colReturnDamagedDamagedQty;
    @FXML
    private TableColumn<MedicineDAO.ReturnDamagedInsightRow, Number> colReturnDamagedTotalQty;
    @FXML
    private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamagedTotalValue;
    @FXML
    private TableColumn<MedicineDAO.ReturnDamagedInsightRow, String> colReturnDamagedRootCauseTags;
    @FXML
    private Label lblSalesMarginWindow;
    @FXML
    private Label lblSalesMarginGrossSales;
    @FXML
    private Label lblSalesMarginNetSales;
    @FXML
    private Label lblSalesMarginGrossMargin;
    @FXML
    private Label lblSalesMarginDiscountBurn;
    @FXML
    private Label lblSubscriptionImpactWindow;
    @FXML
    private Label lblSubscriptionImpactMembersBilled;
    @FXML
    private Label lblSubscriptionImpactSavingsGiven;
    @FXML
    private Label lblSubscriptionImpactRenewalsDue;
    @FXML
    private Label lblSubscriptionImpactOverrideCount;
    @FXML
    private Label lblAnomalyWindow;
    @FXML
    private Label lblAnomalyCount;
    @FXML
    private Label lblAnomalyHighCount;
    @FXML
    private TableView<WeeklyAnomalyAlertEvaluator.AnomalyAlert> anomalyAlertsTable;
    @FXML
    private TableColumn<WeeklyAnomalyAlertEvaluator.AnomalyAlert, String> colAnomalyType;
    @FXML
    private TableColumn<WeeklyAnomalyAlertEvaluator.AnomalyAlert, String> colAnomalySeverity;
    @FXML
    private TableColumn<WeeklyAnomalyAlertEvaluator.AnomalyAlert, String> colAnomalyMetric;
    @FXML
    private TableColumn<WeeklyAnomalyAlertEvaluator.AnomalyAlert, String> colAnomalyThreshold;
    @FXML
    private TableColumn<WeeklyAnomalyAlertEvaluator.AnomalyAlert, String> colAnomalyMessage;
    @FXML
    private Label lblActionTrackerCount;
    @FXML
    private Label lblActionTrackerClosedCount;
    @FXML
    private TableView<AnomalyActionTrackerDAO.ActionTrackerRow> actionTrackerTable;
    @FXML
    private TableColumn<AnomalyActionTrackerDAO.ActionTrackerRow, String> colActionTrackerAlertType;
    @FXML
    private TableColumn<AnomalyActionTrackerDAO.ActionTrackerRow, String> colActionTrackerSeverity;
    @FXML
    private TableColumn<AnomalyActionTrackerDAO.ActionTrackerRow, String> colActionTrackerOwner;
    @FXML
    private TableColumn<AnomalyActionTrackerDAO.ActionTrackerRow, String> colActionTrackerDueDate;
    @FXML
    private TableColumn<AnomalyActionTrackerDAO.ActionTrackerRow, String> colActionTrackerStatus;
    @FXML
    private ComboBox<String> cmbActionClosureStatus;
    @FXML
    private DatePicker dpActionDueDate;
    @FXML
    private ComboBox<String> cmbFilterStore;
    @FXML
    private DatePicker dpFilterStartDate;
    @FXML
    private DatePicker dpFilterEndDate;
    @FXML
    private ComboBox<String> cmbFilterCategory;
    @FXML
    private ComboBox<String> cmbFilterSupplier;
    @FXML
    private Label lblFilterScopeHint;
    @FXML
    private ComboBox<String> cmbAnalyticsExportFormat;
    @FXML
    private ComboBox<String> cmbDispatchChannel;
    @FXML
    private TextField txtDispatchRecipient;
    @FXML
    private ComboBox<String> cmbDispatchFormat;
    @FXML
    private ComboBox<String> cmbDispatchFrequency;
    @FXML
    private Label lblDispatchStatus;

    // Inventory Table (read-only overview)
    @FXML
    private TableView<Medicine> inventoryTable;
    @FXML
    private TableColumn<Medicine, String> colMedicine;
    @FXML
    private TableColumn<Medicine, String> colCompany;
    @FXML
    private TableColumn<Medicine, String> colExpiry;
    @FXML
    private TableColumn<Medicine, Integer> colStock;
    @FXML
    private TableColumn<Medicine, Double> colPrice;
    @FXML
    private TextField searchMedicine;

    // History Table
    @FXML
    private TableView<BillHistoryRecord> historyTable;
    @FXML
    private TableColumn<BillHistoryRecord, Integer> histColId;
    @FXML
    private TableColumn<BillHistoryRecord, String> histColDate;
    @FXML
    private TableColumn<BillHistoryRecord, String> histColCustomer;
    @FXML
    private TableColumn<BillHistoryRecord, String> histColPhone;
    @FXML
    private TableColumn<BillHistoryRecord, Double> histColAmount;
    @FXML
    private Label lblHistoryPageInfo;
    @FXML
    private Button btnHistoryPrev;
    @FXML
    private Button btnHistoryNext;

    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab aiTab;
    @FXML
    private HBox dashboardTopRow;
    @FXML
    private VBox dashboardOperationsHub;
    @FXML
    private Button btnToggleOperationsHub;
    @FXML
    private ToggleButton tglRibbonTopOverview;
    @FXML
    private ToggleButton tglRibbonInventoryRisk;
    @FXML
    private ToggleButton tglRibbonSalesMargin;
    @FXML
    private ToggleButton tglRibbonSubscriptionImpact;
    @FXML
    private ToggleButton tglRibbonAnomalyAlerts;
    @FXML
    private ToggleButton tglRibbonActionTracker;
    @FXML
    private ToggleButton tglRibbonInventoryOverview;
    @FXML
    private VBox cardExpiryBuckets;
    @FXML
    private VBox cardOutOfStock;
    @FXML
    private VBox cardNearStockOut;
    @FXML
    private VBox cardDeadStock;
    @FXML
    private VBox cardFastMoving;
    @FXML
    private VBox cardReturnDamaged;
    @FXML
    private VBox cardSalesMargin;
    @FXML
    private VBox cardSubscriptionImpact;
    @FXML
    private VBox cardAnomalyAlerts;
    @FXML
    private VBox cardActionTracker;
    @FXML
    private VBox cardInventoryOverview;

    // Business Intelligence (AI)
    @FXML
    private TextField substituteInput;
    @FXML
    private TextArea substituteResult;
    @FXML
    private TextArea forecastResult;
    @FXML
    private TextArea expiryResult;

    // Data
    private final ObservableList<Medicine> masterInventoryList = FXCollections.observableArrayList();
    private final ObservableList<BillHistoryRecord> historyList = FXCollections.observableArrayList();
    private static final int OUT_OF_STOCK_LOOKBACK_DAYS = 30;
    private static final int OUT_OF_STOCK_MAX_ROWS = 60;
    private static final int NEAR_STOCK_OUT_LOOKBACK_DAYS = 30;
    private static final int NEAR_STOCK_OUT_REORDER_COVERAGE_DAYS = 7;
    private static final int NEAR_STOCK_OUT_MAX_ROWS = 60;
    private static final int DEAD_STOCK_NO_MOVEMENT_DAYS = 60;
    private static final int DEAD_STOCK_MAX_ROWS = 60;
    private static final int FAST_MOVING_LOOKBACK_DAYS = 30;
    private static final int FAST_MOVING_MAX_ROWS = 60;
    private static final int RETURN_DAMAGED_LOOKBACK_DAYS = 30;
    private static final int RETURN_DAMAGED_MAX_ROWS = 60;
    private static final int ACTION_TRACKER_MAX_ROWS = 200;
    private static final String FILTER_ALL = "All";
    private static final String SINGLE_STORE_LABEL = "Main Store";
    private static final String DISPATCH_OUTBOX_DIR = "reports/dispatch-outbox";
    private static final DateTimeFormatter DISPATCH_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DISPATCH_POLL_SECONDS = 60;
    private static final int HISTORY_PAGE_SIZE = 50;
    private boolean operationsHubVisible = true;
    private int historyPageIndex = 0;
    private int historyTotalCount = 0;
    private LocalDate activeFilterStartDate = LocalDate.now().minusDays(OUT_OF_STOCK_LOOKBACK_DAYS - 1L);
    private LocalDate activeFilterEndDate = LocalDate.now();
    private String activeSupplierFilter = null;
    private String activeCategoryFilter = null;
    private final AtomicBoolean dispatchRunInProgress = new AtomicBoolean(false);
    private ScheduledFuture<?> dispatchPoller;

    // DAOs
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BillDAO billDAO = new BillDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final SubscriptionDAO subscriptionDAO = new SubscriptionDAO();
    private final AnomalyActionTrackerDAO anomalyActionTrackerDAO = new AnomalyActionTrackerDAO();
    private final AnalyticsReportDispatchDAO dispatchDAO = new AnalyticsReportDispatchDAO();
    private final DashboardKpiService kpiService = DashboardKpiService.getInstance();

    private org.example.MediManage.service.ai.InventoryAIService inventoryAIService;

    private void initInventoryAI() {
        this.inventoryAIService = new org.example.MediManage.service.ai.InventoryAIService();
    }

    // ======================== INIT ========================

    @FXML
    private void initialize() {
        userSession = UserSession.getInstance();
        initInventoryAI();

        setupInventoryTable();
        setupAnalyticsFilters();
        setupAnalyticsDispatchControls();
        setupOutOfStockTable();
        setupNearStockOutTable();
        setupDeadStockTable();
        setupFastMovingTable();
        setupReturnDamagedTable();
        setupInsightDrillDowns();
        setupAnomalyAlertsTable();
        setupActionTrackerTable();
        setupHistoryTable();
        setupDashboardTableLayout();
        setOperationsHubVisible(true);
        applyDashboardRibbonVisibility();
        loadInventory();
        startScheduledDispatchPolling();
        loadHistory();
        loadKPIs();
        setupExpenseTab();
    }

    // ======================== INVENTORY (READ-ONLY) ========================

    private void setupInventoryTable() {
        colMedicine.setCellValueFactory(data -> data.getValue().nameProperty());
        colCompany.setCellValueFactory(data -> data.getValue().companyProperty());
        colExpiry.setCellValueFactory(data -> data.getValue().expiryProperty());
        colStock.setCellValueFactory(data -> data.getValue().stockProperty().asObject());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());

        // Search filter
        FilteredList<Medicine> filteredData = new FilteredList<>(masterInventoryList, p -> true);

        searchMedicine.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(medicine -> {
                if (newValue == null || newValue.isEmpty())
                    return true;
                String lowerCaseFilter = newValue.toLowerCase();
                return medicine.getName().toLowerCase().contains(lowerCaseFilter) ||
                        medicine.getCompany().toLowerCase().contains(lowerCaseFilter);
            });
        });

        SortedList<Medicine> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(inventoryTable.comparatorProperty());
        inventoryTable.setItems(sortedData);
    }

    private void setupAnalyticsFilters() {
        if (dpFilterEndDate != null && dpFilterEndDate.getValue() == null) {
            dpFilterEndDate.setValue(LocalDate.now());
        }
        if (dpFilterStartDate != null && dpFilterStartDate.getValue() == null) {
            dpFilterStartDate.setValue(LocalDate.now().minusDays(OUT_OF_STOCK_LOOKBACK_DAYS - 1L));
        }

        if (cmbFilterStore != null) {
            cmbFilterStore.setItems(FXCollections.observableArrayList(SINGLE_STORE_LABEL));
            cmbFilterStore.setValue(SINGLE_STORE_LABEL);
        }
        if (cmbFilterSupplier != null) {
            cmbFilterSupplier.setItems(FXCollections.observableArrayList(FILTER_ALL));
            cmbFilterSupplier.setValue(FILTER_ALL);
        }
        if (cmbFilterCategory != null) {
            cmbFilterCategory.setItems(FXCollections.observableArrayList(FILTER_ALL));
            cmbFilterCategory.setValue(FILTER_ALL);
        }

        UserRole role = userSession != null && userSession.isLoggedIn() && userSession.getUser() != null
                ? userSession.getUser().getRole()
                : null;
        boolean isAdminOrManager = role == UserRole.ADMIN || role == UserRole.MANAGER;
        boolean isPharmacist = role == UserRole.PHARMACIST;

        if (cmbFilterStore != null) {
            cmbFilterStore.setDisable(!isAdminOrManager);
        }
        if (cmbFilterSupplier != null) {
            cmbFilterSupplier.setDisable(!(isAdminOrManager || isPharmacist));
        }
        if (cmbFilterCategory != null) {
            cmbFilterCategory.setDisable(!(isAdminOrManager || isPharmacist));
        }

        if (lblFilterScopeHint != null) {
            if (isAdminOrManager) {
                lblFilterScopeHint.setText("Scope: Admin/Manager can use store, date range, category, and supplier filters.");
            } else if (isPharmacist) {
                lblFilterScopeHint.setText("Scope: Pharmacist can use date range, category, and supplier filters.");
            } else {
                lblFilterScopeHint.setText("Scope: Cashier/Staff can use date range only.");
            }
        }
    }

    private void setupAnalyticsDispatchControls() {
        if (cmbAnalyticsExportFormat != null) {
            cmbAnalyticsExportFormat.setItems(FXCollections.observableArrayList("PDF", "EXCEL", "CSV"));
            cmbAnalyticsExportFormat.setValue("PDF");
        }
        if (cmbDispatchChannel != null) {
            cmbDispatchChannel.setItems(FXCollections.observableArrayList("EMAIL", "WHATSAPP"));
            cmbDispatchChannel.setValue("EMAIL");
        }
        if (cmbDispatchFormat != null) {
            cmbDispatchFormat.setItems(FXCollections.observableArrayList("PDF", "EXCEL", "CSV"));
            cmbDispatchFormat.setValue("PDF");
        }
        if (cmbDispatchFrequency != null) {
            cmbDispatchFrequency.setItems(FXCollections.observableArrayList("DAILY", "WEEKLY", "MONTHLY"));
            cmbDispatchFrequency.setValue("WEEKLY");
        }
        if (lblDispatchStatus != null) {
            lblDispatchStatus.setText("Dispatch: no schedules created yet.");
        }
    }

    @FXML
    private void handleExportAnalyticsReport() {
        String selectedFormat = cmbAnalyticsExportFormat == null ? "PDF" : cmbAnalyticsExportFormat.getValue();
        ReportService.AnalyticsExportFormat format = ReportService.AnalyticsExportFormat.fromValue(selectedFormat);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Analytics Report");
        chooser.setInitialFileName("Analytics_Report_" + LocalDate.now() + "." + format.fileExtension());
        switch (format) {
            case CSV -> chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            case EXCEL -> chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            case PDF -> chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        }

        File file = chooser.showSaveDialog(inventoryTable == null ? null : inventoryTable.getScene().getWindow());
        if (file == null) {
            return;
        }

        AnalyticsFilterCriteria filters = resolveCurrentFilterCriteria();
        AppExecutors.runBackground(() -> {
            try {
                AnalyticsDataBundle bundle = fetchAnalyticsData(filters, false);
                ReportService.AnalyticsExportPayload payload = buildAnalyticsExportPayload(bundle, "Manual Export");
                reportService.exportAnalyticsReport(payload, format, file.getAbsolutePath());
                Platform.runLater(() -> showAlert(
                        Alert.AlertType.INFORMATION,
                        "Export Complete",
                        "Analytics report exported to:\n" + file.getAbsolutePath()));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleScheduleAnalyticsDispatch() {
        UserRole role = userSession != null && userSession.isLoggedIn() && userSession.getUser() != null
                ? userSession.getUser().getRole()
                : null;
        if (role != UserRole.ADMIN && role != UserRole.MANAGER) {
            showAlert(Alert.AlertType.WARNING, "Permission Denied", "Only Manager/Admin can schedule report dispatch.");
            return;
        }

        String recipient = txtDispatchRecipient == null ? null : txtDispatchRecipient.getText();
        if (recipient == null || recipient.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Recipient is required for scheduled dispatch.");
            return;
        }

        String channel = cmbDispatchChannel == null ? "EMAIL" : cmbDispatchChannel.getValue();
        String format = cmbDispatchFormat == null ? "PDF" : cmbDispatchFormat.getValue();
        String frequency = cmbDispatchFrequency == null ? "WEEKLY" : cmbDispatchFrequency.getValue();
        AnalyticsFilterCriteria filters = resolveCurrentFilterCriteria();
        LocalDateTime nextRunAt = calculateNextRunAt(LocalDateTime.now(), frequency);

        Integer createdBy = userSession != null && userSession.isLoggedIn() && userSession.getUser() != null
                ? userSession.getUser().getId()
                : null;

        try {
            int scheduleId = dispatchDAO.createSchedule(
                    channel,
                    recipient.trim(),
                    format,
                    frequency,
                    ZoneId.systemDefault().getId(),
                    filters.startDate(),
                    filters.endDate(),
                    filters.supplierFilter(),
                    filters.categoryFilter(),
                    createdBy,
                    nextRunAt);
            updateDispatchStatus("Dispatch scheduled (#" + scheduleId + ") next run: " + nextRunAt.format(DISPATCH_TS));
            showAlert(Alert.AlertType.INFORMATION, "Schedule Created",
                    "Dispatch schedule #" + scheduleId + " created.\nNext run: " + nextRunAt.format(DISPATCH_TS));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Schedule Failed", e.getMessage());
        }
    }

    @FXML
    private void handleRunDueDispatchesNow() {
        AppExecutors.runBackground(() -> processDueDispatchSchedules(true));
    }

    private AnalyticsFilterCriteria resolveCurrentFilterCriteria() {
        LocalDate endDate = dpFilterEndDate == null ? null : dpFilterEndDate.getValue();
        LocalDate startDate = dpFilterStartDate == null ? null : dpFilterStartDate.getValue();
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(OUT_OF_STOCK_LOOKBACK_DAYS - 1L);
        }
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
            if (dpFilterStartDate != null) {
                dpFilterStartDate.setValue(startDate);
            }
            if (dpFilterEndDate != null) {
                dpFilterEndDate.setValue(endDate);
            }
        }

        String supplierFilter = selectedFilterValue(cmbFilterSupplier);
        String categoryFilter = selectedFilterValue(cmbFilterCategory);
        String storeFilter = selectedFilterValue(cmbFilterStore);
        if (storeFilter == null) {
            storeFilter = SINGLE_STORE_LABEL;
        }
        return new AnalyticsFilterCriteria(startDate, endDate, supplierFilter, categoryFilter, storeFilter);
    }

    private String selectedFilterValue(ComboBox<String> comboBox) {
        if (comboBox == null) {
            return null;
        }
        String value = comboBox.getValue();
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || FILTER_ALL.equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private List<Medicine> applyInventoryFilters(List<Medicine> inventory, AnalyticsFilterCriteria filters) {
        if (inventory == null || inventory.isEmpty()) {
            return List.of();
        }
        return inventory.stream()
                .filter(medicine -> matchesFilter(medicine.getCompany(), filters.supplierFilter()))
                .filter(medicine -> matchesFilter(medicine.getGenericName(), filters.categoryFilter()))
                .toList();
    }

    private boolean matchesFilter(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.trim().equalsIgnoreCase(filter.trim());
    }

    private void refreshFilterChoices(List<Medicine> inventory) {
        if (inventory == null) {
            return;
        }
        if (cmbFilterSupplier != null) {
            String currentValue = cmbFilterSupplier.getValue();
            List<String> suppliers = inventory.stream()
                    .map(Medicine::getCompany)
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<String> options = new ArrayList<>();
            options.add(FILTER_ALL);
            options.addAll(suppliers);
            cmbFilterSupplier.setItems(FXCollections.observableArrayList(options));
            cmbFilterSupplier.setValue(options.contains(currentValue) ? currentValue : FILTER_ALL);
        }
        if (cmbFilterCategory != null) {
            String currentValue = cmbFilterCategory.getValue();
            List<String> categories = inventory.stream()
                    .map(Medicine::getGenericName)
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<String> options = new ArrayList<>();
            options.add(FILTER_ALL);
            options.addAll(categories);
            cmbFilterCategory.setItems(FXCollections.observableArrayList(options));
            cmbFilterCategory.setValue(options.contains(currentValue) ? currentValue : FILTER_ALL);
        }
    }

    private String activeFilterScopeText() {
        String supplier = activeSupplierFilter == null ? FILTER_ALL : activeSupplierFilter;
        String category = activeCategoryFilter == null ? FILTER_ALL : activeCategoryFilter;
        return " | Supplier: " + supplier + " | Category: " + category;
    }

    private record AnalyticsFilterCriteria(
            LocalDate startDate,
            LocalDate endDate,
            String supplierFilter,
            String categoryFilter,
            String storeFilter) {
    }

    private record AnalyticsDataBundle(
            AnalyticsFilterCriteria filters,
            List<Medicine> allInventory,
            List<Medicine> inventory,
            List<MedicineDAO.OutOfStockInsightRow> outOfStockInsights,
            List<MedicineDAO.NearStockOutInsightRow> nearStockOutInsights,
            List<MedicineDAO.DeadStockInsightRow> deadStockInsights,
            List<MedicineDAO.FastMovingInsightRow> fastMovingInsights,
            List<MedicineDAO.ReturnDamagedInsightRow> returnDamagedInsights,
            BillDAO.WeeklySalesMarginSummary salesMarginSummary,
            SubscriptionDAO.WeeklyAnalyticsSummaryRow subscriptionImpactSummary,
            List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> anomalyAlerts,
            List<AnomalyActionTrackerDAO.ActionTrackerRow> actionTrackerRows) {
    }

    private AnalyticsDataBundle fetchAnalyticsData(AnalyticsFilterCriteria filters, boolean syncActionTracker) {
        AnalyticsFilterCriteria safeFilters = filters == null ? resolveCurrentFilterCriteria() : filters;
        var allInventory = medicineDAO.getAllMedicines();
        var inventory = applyInventoryFilters(allInventory, safeFilters);
        var outOfStockInsights = medicineDAO.getOutOfStockInsights(
                safeFilters.startDate(),
                safeFilters.endDate(),
                safeFilters.supplierFilter(),
                safeFilters.categoryFilter(),
                OUT_OF_STOCK_MAX_ROWS);
        var nearStockOutInsights = medicineDAO.getNearStockOutInsights(
                safeFilters.startDate(),
                safeFilters.endDate(),
                NEAR_STOCK_OUT_REORDER_COVERAGE_DAYS,
                safeFilters.supplierFilter(),
                safeFilters.categoryFilter(),
                NEAR_STOCK_OUT_MAX_ROWS);
        var deadStockInsights = medicineDAO.getDeadStockInsights(
                safeFilters.endDate(),
                DEAD_STOCK_NO_MOVEMENT_DAYS,
                safeFilters.supplierFilter(),
                safeFilters.categoryFilter(),
                DEAD_STOCK_MAX_ROWS);
        var fastMovingInsights = medicineDAO.getFastMovingInsights(
                safeFilters.startDate(),
                safeFilters.endDate(),
                safeFilters.supplierFilter(),
                safeFilters.categoryFilter(),
                FAST_MOVING_MAX_ROWS);
        var returnDamagedInsights = medicineDAO.getReturnDamagedInsights(
                safeFilters.startDate(),
                safeFilters.endDate(),
                safeFilters.supplierFilter(),
                safeFilters.categoryFilter(),
                RETURN_DAMAGED_MAX_ROWS);

        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(LocalDate.now());
        var salesMarginSummary = billDAO.getWeeklySalesMarginSummary(weeklyWindow.startDate(), weeklyWindow.endDate());
        String timezoneName = ZoneId.systemDefault().getId();
        var subscriptionImpactSummary = subscriptionDAO
                .refreshWeeklyAnalyticsSummary(LocalDate.now(), timezoneName)
                .or(() -> subscriptionDAO.getWeeklyAnalyticsSummary(LocalDate.now(), timezoneName))
                .orElse(null);
        var leakageHistory = subscriptionDAO.getRecentWeeklyLeakageHistory(LocalDate.now(), timezoneName, 8);
        var anomalyAlerts = WeeklyAnomalyAlertEvaluator.evaluate(
                inventory,
                outOfStockInsights,
                subscriptionImpactSummary,
                leakageHistory,
                LocalDate.now());
        Integer defaultActionOwnerUserId = resolveDefaultActionOwnerUserId();
        List<AnomalyActionTrackerDAO.ActionTrackerRow> actionTrackerRows = syncActionTracker
                ? anomalyActionTrackerDAO.syncAndGetWeeklyActions(
                        LocalDate.now(),
                        timezoneName,
                        anomalyAlerts,
                        defaultActionOwnerUserId)
                : anomalyActionTrackerDAO.getWeeklyActions(
                        LocalDate.now(),
                        timezoneName,
                        ACTION_TRACKER_MAX_ROWS);

        return new AnalyticsDataBundle(
                safeFilters,
                allInventory,
                inventory,
                outOfStockInsights,
                nearStockOutInsights,
                deadStockInsights,
                fastMovingInsights,
                returnDamagedInsights,
                salesMarginSummary,
                subscriptionImpactSummary,
                anomalyAlerts,
                actionTrackerRows);
    }

    private void loadInventory() {
        AnalyticsFilterCriteria filters = resolveCurrentFilterCriteria();
        AppExecutors.runBackground(() -> {
            AnalyticsDataBundle bundle = fetchAnalyticsData(filters, true);
            activeFilterStartDate = bundle.filters().startDate();
            activeFilterEndDate = bundle.filters().endDate();
            activeSupplierFilter = bundle.filters().supplierFilter();
            activeCategoryFilter = bundle.filters().categoryFilter();

            Platform.runLater(() -> {
                masterInventoryList.setAll(bundle.inventory());
                refreshFilterChoices(bundle.allInventory());
                updateOutOfStockPanel(bundle.outOfStockInsights());
                updateNearStockOutPanel(bundle.nearStockOutInsights());
                updateDeadStockPanel(bundle.deadStockInsights());
                updateFastMovingPanel(bundle.fastMovingInsights());
                updateReturnDamagedPanel(bundle.returnDamagedInsights());
                updateSalesAndMarginPanel(bundle.salesMarginSummary());
                updateSubscriptionImpactPanel(bundle.subscriptionImpactSummary());
                updateAnomalyAlertsPanel(bundle.anomalyAlerts());
                updateActionTrackerPanel(bundle.actionTrackerRows());
                loadKPIs();
                checkExpiryAlerts();
            });
        });
    }

    @FXML
    private void handleApplyAnalyticsFilters() {
        loadInventory();
    }

    @FXML
    private void handleResetAnalyticsFilters() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(OUT_OF_STOCK_LOOKBACK_DAYS - 1L);
        if (dpFilterStartDate != null) {
            dpFilterStartDate.setValue(startDate);
        }
        if (dpFilterEndDate != null) {
            dpFilterEndDate.setValue(endDate);
        }
        if (cmbFilterSupplier != null) {
            cmbFilterSupplier.setValue(FILTER_ALL);
        }
        if (cmbFilterCategory != null) {
            cmbFilterCategory.setValue(FILTER_ALL);
        }
        if (cmbFilterStore != null) {
            cmbFilterStore.setValue(SINGLE_STORE_LABEL);
        }
        loadInventory();
    }

    @FXML
    private void handleToggleOperationsHub() {
        setOperationsHubVisible(!operationsHubVisible);
    }

    private void setOperationsHubVisible(boolean visible) {
        operationsHubVisible = visible;
        if (dashboardOperationsHub != null) {
            dashboardOperationsHub.setVisible(visible);
            dashboardOperationsHub.setManaged(visible);
        }
        if (dashboardTopRow != null) {
            dashboardTopRow.setSpacing(visible ? 16 : 0);
        }
        if (btnToggleOperationsHub != null) {
            btnToggleOperationsHub.setText(visible ? "Hide Ops Hub" : "Show Ops Hub");
        }
    }

    @FXML
    private void handleDashboardRibbonToggles() {
        applyDashboardRibbonVisibility();
    }

    @FXML
    private void handleResetDashboardRibbon() {
        if (tglRibbonTopOverview != null) {
            tglRibbonTopOverview.setSelected(true);
        }
        if (tglRibbonInventoryRisk != null) {
            tglRibbonInventoryRisk.setSelected(true);
        }
        if (tglRibbonSalesMargin != null) {
            tglRibbonSalesMargin.setSelected(true);
        }
        if (tglRibbonSubscriptionImpact != null) {
            tglRibbonSubscriptionImpact.setSelected(true);
        }
        if (tglRibbonAnomalyAlerts != null) {
            tglRibbonAnomalyAlerts.setSelected(true);
        }
        if (tglRibbonActionTracker != null) {
            tglRibbonActionTracker.setSelected(true);
        }
        if (tglRibbonInventoryOverview != null) {
            tglRibbonInventoryOverview.setSelected(true);
        }
        applyDashboardRibbonVisibility();
    }

    private void applyDashboardRibbonVisibility() {
        setNodeVisibleManaged(dashboardTopRow, ribbonSelected(tglRibbonTopOverview));

        boolean showInventoryRisk = ribbonSelected(tglRibbonInventoryRisk);
        setNodeVisibleManaged(cardExpiryBuckets, showInventoryRisk);
        setNodeVisibleManaged(cardOutOfStock, showInventoryRisk);
        setNodeVisibleManaged(cardNearStockOut, showInventoryRisk);
        setNodeVisibleManaged(cardDeadStock, showInventoryRisk);
        setNodeVisibleManaged(cardFastMoving, showInventoryRisk);
        setNodeVisibleManaged(cardReturnDamaged, showInventoryRisk);

        setNodeVisibleManaged(cardSalesMargin, ribbonSelected(tglRibbonSalesMargin));
        setNodeVisibleManaged(cardSubscriptionImpact, ribbonSelected(tglRibbonSubscriptionImpact));
        setNodeVisibleManaged(cardAnomalyAlerts, ribbonSelected(tglRibbonAnomalyAlerts));
        setNodeVisibleManaged(cardActionTracker, ribbonSelected(tglRibbonActionTracker));
        setNodeVisibleManaged(cardInventoryOverview, ribbonSelected(tglRibbonInventoryOverview));
    }

    private boolean ribbonSelected(ToggleButton toggleButton) {
        return toggleButton == null || toggleButton.isSelected();
    }

    private void setNodeVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void setupDashboardTableLayout() {
        configureDashboardTable(outOfStockTable, 320, 220);
        configureDashboardTable(nearStockOutTable, 320, 220);
        configureDashboardTable(deadStockTable, 320, 220);
        configureDashboardTable(fastMovingTable, 320, 220);
        configureDashboardTable(returnDamagedTable, 320, 220);
        configureDashboardTable(anomalyAlertsTable, 320, 220);
        configureDashboardTable(actionTrackerTable, 320, 220);
        configureDashboardTable(inventoryTable, 360, 260);
    }

    private void configureDashboardTable(TableView<?> table, double prefHeight, double minHeight) {
        if (table == null) {
            return;
        }
        table.setFixedCellSize(30);
        table.setPrefHeight(prefHeight);
        table.setMinHeight(minHeight);
    }

    private void startScheduledDispatchPolling() {
        if (dispatchPoller != null && !dispatchPoller.isCancelled()) {
            return;
        }
        AppExecutors.runBackground(() -> processDueDispatchSchedules(false));
        dispatchPoller = AppExecutors.scheduleAtFixedRate(
                () -> processDueDispatchSchedules(false),
                DISPATCH_POLL_SECONDS,
                DISPATCH_POLL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void processDueDispatchSchedules(boolean runAllActive) {
        if (!dispatchRunInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            List<AnalyticsReportDispatchDAO.DispatchScheduleRow> schedules = runAllActive
                    ? dispatchDAO.getActiveSchedules(200)
                    : dispatchDAO.getDueSchedules(now, 200);
            if (schedules.isEmpty()) {
                if (runAllActive) {
                    updateDispatchStatus("Dispatch run: no active schedules found.");
                }
                return;
            }

            int successCount = 0;
            int failureCount = 0;
            for (AnalyticsReportDispatchDAO.DispatchScheduleRow schedule : schedules) {
                LocalDateTime runAt = LocalDateTime.now();
                LocalDateTime nextRunAt = calculateNextRunAt(runAt, schedule.frequency());
                try {
                    dispatchScheduledReport(schedule);
                    dispatchDAO.markRunSuccess(schedule.scheduleId(), runAt, nextRunAt);
                    successCount++;
                } catch (Exception e) {
                    dispatchDAO.markRunFailure(schedule.scheduleId(), runAt, nextRunAt, e.getMessage());
                    failureCount++;
                }
            }
            updateDispatchStatus("Dispatch run complete. Success: " + successCount + ", Failed: " + failureCount + ".");
        } finally {
            dispatchRunInProgress.set(false);
        }
    }

    private void dispatchScheduledReport(AnalyticsReportDispatchDAO.DispatchScheduleRow schedule) throws Exception {
        AnalyticsFilterCriteria filters = resolveFiltersFromSchedule(schedule);
        AnalyticsDataBundle bundle = fetchAnalyticsData(filters, false);
        ReportService.AnalyticsExportPayload payload = buildAnalyticsExportPayload(
                bundle,
                "Scheduled Dispatch #" + schedule.scheduleId());

        ReportService.AnalyticsExportFormat format = ReportService.AnalyticsExportFormat.fromValue(schedule.reportFormat());
        Path outboxDir = Path.of(DISPATCH_OUTBOX_DIR);
        Files.createDirectories(outboxDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportFileName = "analytics_dispatch_" + schedule.scheduleId() + "_" + timestamp + "."
                + format.fileExtension();
        Path reportFile = outboxDir.resolve(reportFileName);

        reportService.exportAnalyticsReport(payload, format, reportFile.toString());
        writeDispatchEnvelope(schedule, reportFile, payload);
    }

    private void writeDispatchEnvelope(
            AnalyticsReportDispatchDAO.DispatchScheduleRow schedule,
            Path reportFile,
            ReportService.AnalyticsExportPayload payload) throws IOException {
        String baseName = reportFile.getFileName().toString();
        int lastDot = baseName.lastIndexOf('.');
        String envelopeName = (lastDot > 0 ? baseName.substring(0, lastDot) : baseName) + ".dispatch.txt";
        Path envelopePath = reportFile.resolveSibling(envelopeName);
        StringBuilder content = new StringBuilder();
        content.append("Dispatch Channel: ").append(schedule.channel()).append('\n');
        content.append("Recipient: ").append(schedule.recipient()).append('\n');
        content.append("Report Format: ").append(schedule.reportFormat()).append('\n');
        content.append("Generated At: ").append(payload.generatedAt()).append('\n');
        content.append("Attachment: ").append(reportFile.toAbsolutePath()).append('\n');
        content.append("Status: QUEUED_FOR_GATEWAY\n");
        Files.writeString(envelopePath, content.toString());
    }

    private AnalyticsFilterCriteria resolveFiltersFromSchedule(AnalyticsReportDispatchDAO.DispatchScheduleRow schedule) {
        LocalDate endDate = parseOptionalLocalDate(schedule.filterEndDate());
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        LocalDate startDate = parseOptionalLocalDate(schedule.filterStartDate());
        if (startDate == null) {
            startDate = endDate.minusDays(OUT_OF_STOCK_LOOKBACK_DAYS - 1L);
        }
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }
        return new AnalyticsFilterCriteria(
                startDate,
                endDate,
                normalizeScheduleFilter(schedule.supplierFilter()),
                normalizeScheduleFilter(schedule.categoryFilter()),
                SINGLE_STORE_LABEL);
    }

    private String normalizeScheduleFilter(String value) {
        if (value == null || value.isBlank() || FILTER_ALL.equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private LocalDate parseOptionalLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String normalized = value.trim();
            if (normalized.length() > 10) {
                normalized = normalized.substring(0, 10);
            }
            return LocalDate.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime calculateNextRunAt(LocalDateTime base, String frequency) {
        LocalDateTime safeBase = base == null ? LocalDateTime.now() : base;
        String normalized = frequency == null ? "WEEKLY" : frequency.trim().toUpperCase();
        return switch (normalized) {
            case "DAILY" -> safeBase.plusDays(1);
            case "MONTHLY" -> safeBase.plusMonths(1);
            default -> safeBase.plusWeeks(1);
        };
    }

    private void updateDispatchStatus(String message) {
        Platform.runLater(() -> {
            if (lblDispatchStatus != null) {
                lblDispatchStatus.setText(message);
            }
        });
    }

    private ReportService.AnalyticsExportPayload buildAnalyticsExportPayload(
            AnalyticsDataBundle bundle,
            String reportSourceLabel) {
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("Generated by: " + reportSourceLabel);
        summaryLines.add("Inventory records: " + bundle.inventory().size());
        summaryLines.add("Out-of-stock SKUs: " + bundle.outOfStockInsights().size()
                + " | Revenue impact: " + formatCurrencyAmount(
                        bundle.outOfStockInsights().stream()
                                .mapToDouble(MedicineDAO.OutOfStockInsightRow::estimatedRevenueImpact)
                                .sum()));
        summaryLines.add("Near stock-out SKUs: " + bundle.nearStockOutInsights().size()
                + " | Revenue at risk: " + formatCurrencyAmount(
                        bundle.nearStockOutInsights().stream()
                                .mapToDouble(MedicineDAO.NearStockOutInsightRow::estimatedRevenueAtRisk)
                                .sum()));
        summaryLines.add("Dead stock SKUs: " + bundle.deadStockInsights().size()
                + " | Locked value: " + formatCurrencyAmount(
                        bundle.deadStockInsights().stream()
                                .mapToDouble(MedicineDAO.DeadStockInsightRow::deadStockValue)
                                .sum()));
        summaryLines.add("Fast-moving SKUs: " + bundle.fastMovingInsights().size()
                + " | Revenue: " + formatCurrencyAmount(
                        bundle.fastMovingInsights().stream()
                                .mapToDouble(MedicineDAO.FastMovingInsightRow::lookbackRevenue)
                                .sum()));
        summaryLines.add("Return/Damaged total value: " + formatCurrencyAmount(
                bundle.returnDamagedInsights().stream()
                        .mapToDouble(MedicineDAO.ReturnDamagedInsightRow::totalValue)
                        .sum()));
        if (bundle.salesMarginSummary() != null) {
            summaryLines.add("Sales Margin -> Gross: " + formatCurrencyAmount(bundle.salesMarginSummary().grossSales())
                    + ", Net: " + formatCurrencyAmount(bundle.salesMarginSummary().netSales())
                    + ", Margin: " + formatCurrencyAmount(bundle.salesMarginSummary().grossMargin())
                    + " (" + String.format("%.2f%%", bundle.salesMarginSummary().grossMarginPercent()) + ")");
        }
        if (bundle.subscriptionImpactSummary() != null) {
            summaryLines.add("Subscription Impact -> Members billed: "
                    + bundle.subscriptionImpactSummary().subscriptionBillCount()
                    + ", Savings: " + formatCurrencyAmount(bundle.subscriptionImpactSummary().totalSavings())
                    + ", Renewals due (7d): " + bundle.subscriptionImpactSummary().renewalsDueNext7Days()
                    + ", Pending overrides: " + bundle.subscriptionImpactSummary().pendingOverrideCount());
        }
        summaryLines.add("Anomaly alerts: " + bundle.anomalyAlerts().size()
                + " | Action tracker rows: " + bundle.actionTrackerRows().size());

        List<ReportService.AnalyticsReportSection> sections = new ArrayList<>();
        sections.add(new ReportService.AnalyticsReportSection(
                "Out of Stock",
                List.of("Medicine", "Supplier", "Days Out", "Last Sale", "Avg Daily Revenue", "Revenue Impact"),
                bundle.outOfStockInsights().stream()
                        .map(row -> List.of(
                                row.medicineName(),
                                row.company(),
                                String.valueOf(row.daysOutOfStock()),
                                row.lastSaleAt() == null ? "-" : row.lastSaleAt(),
                                String.format("%.2f", row.averageDailyRevenue()),
                                String.format("%.2f", row.estimatedRevenueImpact())))
                        .toList()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Near Stock-Out",
                List.of("Medicine", "Supplier", "Stock", "Avg Daily Cons.", "Reorder Threshold", "Days Left", "Revenue At Risk"),
                bundle.nearStockOutInsights().stream()
                        .map(row -> List.of(
                                row.medicineName(),
                                row.company(),
                                String.valueOf(row.currentStock()),
                                String.format("%.2f", row.averageDailyConsumption()),
                                String.valueOf(row.reorderThresholdQty()),
                                String.format("%.2f", row.daysToStockOut()),
                                String.format("%.2f", row.estimatedRevenueAtRisk())))
                        .toList()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Dead Stock",
                List.of("Medicine", "Supplier", "Stock", "Last Sale", "Days Stagnant", "Dead Stock Value"),
                bundle.deadStockInsights().stream()
                        .map(row -> List.of(
                                row.medicineName(),
                                row.company(),
                                String.valueOf(row.currentStock()),
                                row.lastSaleAt() == null ? "-" : row.lastSaleAt(),
                                String.valueOf(row.daysSinceLastMovement()),
                                String.format("%.2f", row.deadStockValue())))
                        .toList()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Fast-Moving",
                List.of("Medicine", "Supplier", "Units Sold", "Revenue", "Avg Daily Units", "Avg Daily Revenue", "Last Sale"),
                bundle.fastMovingInsights().stream()
                        .map(row -> List.of(
                                row.medicineName(),
                                row.company(),
                                String.format("%.2f", row.lookbackUnitsSold()),
                                String.format("%.2f", row.lookbackRevenue()),
                                String.format("%.2f", row.averageDailyUnits()),
                                String.format("%.2f", row.averageDailyRevenue()),
                                row.lastSaleAt() == null ? "-" : row.lastSaleAt()))
                        .toList()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Return/Damaged",
                List.of("Medicine", "Supplier", "Returned Qty", "Damaged Qty", "Total Qty", "Total Value", "Root Cause Tags"),
                bundle.returnDamagedInsights().stream()
                        .map(row -> List.of(
                                row.medicineName(),
                                row.company(),
                                String.valueOf(row.returnedQuantity()),
                                String.valueOf(row.damagedQuantity()),
                                String.valueOf(row.totalQuantity()),
                                String.format("%.2f", row.totalValue()),
                                row.rootCauseTags()))
                        .toList()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Weekly Anomaly Alerts",
                List.of("Alert Type", "Severity", "Metric", "Threshold", "Message"),
                bundle.anomalyAlerts().stream()
                        .map(row -> List.of(
                                row.alertType(),
                                row.severity(),
                                row.metricValue(),
                                row.thresholdRule(),
                                row.message()))
                        .toList()));
        sections.add(new ReportService.AnalyticsReportSection(
                "Action Tracker",
                List.of("Alert Type", "Severity", "Owner", "Due Date", "Status"),
                bundle.actionTrackerRows().stream()
                        .map(row -> List.of(
                                row.alertType(),
                                row.severity(),
                                row.ownerUsername() == null || row.ownerUsername().isBlank()
                                        ? (row.ownerUserId() == null ? "-" : "User #" + row.ownerUserId())
                                        : row.ownerUsername(),
                                row.dueDate() == null ? "-" : row.dueDate(),
                                row.closureStatus()))
                        .toList()));

        AnalyticsFilterCriteria filters = bundle.filters();
        String supplier = filters.supplierFilter() == null ? FILTER_ALL : filters.supplierFilter();
        String category = filters.categoryFilter() == null ? FILTER_ALL : filters.categoryFilter();
        String filterScope = "Range: " + filters.startDate() + " to " + filters.endDate()
                + " | Supplier: " + supplier
                + " | Category: " + category
                + " | Store: " + filters.storeFilter();

        return new ReportService.AnalyticsExportPayload(
                "MediManage Weekly Analytics Report",
                LocalDateTime.now().format(DISPATCH_TS),
                filterScope,
                summaryLines,
                sections);
    }

    private String formatCurrencyAmount(double amount) {
        return String.format("₹%.2f", amount);
    }

    // ======================== KPIs ========================

    private void loadKPIs() {
        AppExecutors.runBackground(() -> {
            DashboardKpiService.DashboardKpis kpis = kpiService.getDashboardKpis(masterInventoryList);
            Platform.runLater(() -> {
                dailySales.setText(String.format("₹%.2f", kpis.dailySales()));
                if (lowStock != null)
                    lowStock.setText(String.valueOf(kpis.lowStockCount()));
                if (totalProfit != null)
                    totalProfit.setText(String.format("₹%.2f", kpis.netProfit()));
                if (pendingRx != null)
                    pendingRx.setText(String.valueOf(kpis.pendingRxCount()));
                if (activeSubscribers != null)
                    activeSubscribers.setText(String.valueOf(kpis.activeSubscribers()));
                if (renewalsDue != null)
                    renewalsDue.setText(String.valueOf(kpis.renewalsDueSoon()));
                if (subscriptionDiscountValue != null)
                    subscriptionDiscountValue.setText(String.format("₹%.2f", kpis.dailySubscriptionSavings()));
                if (pendingOverrides != null)
                    pendingOverrides.setText(String.valueOf(kpis.pendingOverrideCount()));
                if (lblExpiryExpired != null)
                    lblExpiryExpired.setText(String.valueOf(kpis.expiredMedicinesCount()));
                if (lblExpiry0To30 != null)
                    lblExpiry0To30.setText(String.valueOf(kpis.expiry0To30DaysCount()));
                if (lblExpiry31To60 != null)
                    lblExpiry31To60.setText(String.valueOf(kpis.expiry31To60DaysCount()));
                if (lblExpiry61To90 != null)
                    lblExpiry61To90.setText(String.valueOf(kpis.expiry61To90DaysCount()));
                if (lblExpiryWeekWindow != null) {
                    ReportingWindowUtils.WeeklyWindow weeklyWindow =
                            ReportingWindowUtils.mondayToSundayWindow(LocalDate.now());
                    lblExpiryWeekWindow.setText("Week: " + weeklyWindow.startDate()
                            + " to " + weeklyWindow.endDate()
                            + " (" + ZoneId.systemDefault().getId() + ")");
                }
            });
        });
    }

    private void setupOutOfStockTable() {
        if (outOfStockTable == null) {
            return;
        }
        if (colOutOfStockMedicine != null) {
            colOutOfStockMedicine.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicineName()));
        }
        if (colOutOfStockCompany != null) {
            colOutOfStockCompany.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().company()));
        }
        if (colOutOfStockDays != null) {
            colOutOfStockDays.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().daysOutOfStock()));
        }
        if (colOutOfStockLastSale != null) {
            colOutOfStockLastSale.setCellValueFactory(data -> {
                String value = data.getValue().lastSaleAt();
                return new SimpleStringProperty(value == null || value.isBlank() ? "-" : value);
            });
        }
        if (colOutOfStockAvgDailyRevenue != null) {
            colOutOfStockAvgDailyRevenue.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().averageDailyRevenue())));
        }
        if (colOutOfStockRevenueImpact != null) {
            colOutOfStockRevenueImpact.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().estimatedRevenueImpact())));
        }
        outOfStockTable.setItems(FXCollections.observableArrayList());
    }

    private void updateOutOfStockPanel(List<MedicineDAO.OutOfStockInsightRow> rows) {
        List<MedicineDAO.OutOfStockInsightRow> safeRows = rows == null ? List.of() : rows;
        if (outOfStockTable != null) {
            outOfStockTable.setItems(FXCollections.observableArrayList(safeRows));
        }
        if (lblOutOfStockWindow != null) {
            lblOutOfStockWindow.setText(
                    "Range: " + activeFilterStartDate + " to " + activeFilterEndDate + activeFilterScopeText());
        }
        if (lblOutOfStockSkuCount != null) {
            lblOutOfStockSkuCount.setText(String.valueOf(safeRows.size()));
        }
        if (lblOutOfStockAvgDays != null) {
            double avgDays = safeRows.stream()
                    .mapToLong(MedicineDAO.OutOfStockInsightRow::daysOutOfStock)
                    .average()
                    .orElse(0.0);
            lblOutOfStockAvgDays.setText(String.format("%.1f", avgDays));
        }
        if (lblOutOfStockRevenueImpact != null) {
            double totalImpact = safeRows.stream()
                    .mapToDouble(MedicineDAO.OutOfStockInsightRow::estimatedRevenueImpact)
                    .sum();
            lblOutOfStockRevenueImpact.setText(String.format("₹%.2f", totalImpact));
        }
    }

    private void setupNearStockOutTable() {
        if (nearStockOutTable == null) {
            return;
        }
        if (colNearStockOutMedicine != null) {
            colNearStockOutMedicine.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicineName()));
        }
        if (colNearStockOutCompany != null) {
            colNearStockOutCompany.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().company()));
        }
        if (colNearStockOutStock != null) {
            colNearStockOutStock.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().currentStock()));
        }
        if (colNearStockOutAvgDailyConsumption != null) {
            colNearStockOutAvgDailyConsumption.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("%.2f", data.getValue().averageDailyConsumption())));
        }
        if (colNearStockOutThreshold != null) {
            colNearStockOutThreshold.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().reorderThresholdQty()));
        }
        if (colNearStockOutDaysLeft != null) {
            colNearStockOutDaysLeft.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("%.2f", data.getValue().daysToStockOut())));
        }
        if (colNearStockOutRevenueAtRisk != null) {
            colNearStockOutRevenueAtRisk.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().estimatedRevenueAtRisk())));
        }
        nearStockOutTable.setItems(FXCollections.observableArrayList());
    }

    private void updateNearStockOutPanel(List<MedicineDAO.NearStockOutInsightRow> rows) {
        List<MedicineDAO.NearStockOutInsightRow> safeRows = rows == null ? List.of() : rows;
        if (nearStockOutTable != null) {
            nearStockOutTable.setItems(FXCollections.observableArrayList(safeRows));
        }
        if (lblNearStockOutWindow != null) {
            lblNearStockOutWindow.setText("Range: " + activeFilterStartDate + " to " + activeFilterEndDate
                    + activeFilterScopeText()
                    + " | Reorder coverage: " + NEAR_STOCK_OUT_REORDER_COVERAGE_DAYS + " days");
        }
        if (lblNearStockOutSkuCount != null) {
            lblNearStockOutSkuCount.setText(String.valueOf(safeRows.size()));
        }
        if (lblNearStockOutAvgDaysLeft != null) {
            double avgDays = safeRows.stream()
                    .mapToDouble(MedicineDAO.NearStockOutInsightRow::daysToStockOut)
                    .average()
                    .orElse(0.0);
            lblNearStockOutAvgDaysLeft.setText(String.format("%.2f", avgDays));
        }
        if (lblNearStockOutRevenueAtRisk != null) {
            double totalRisk = safeRows.stream()
                    .mapToDouble(MedicineDAO.NearStockOutInsightRow::estimatedRevenueAtRisk)
                    .sum();
            lblNearStockOutRevenueAtRisk.setText(String.format("₹%.2f", totalRisk));
        }
    }

    private void setupDeadStockTable() {
        if (deadStockTable == null) {
            return;
        }
        if (colDeadStockMedicine != null) {
            colDeadStockMedicine.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicineName()));
        }
        if (colDeadStockCompany != null) {
            colDeadStockCompany.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().company()));
        }
        if (colDeadStockStock != null) {
            colDeadStockStock.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().currentStock()));
        }
        if (colDeadStockLastSale != null) {
            colDeadStockLastSale.setCellValueFactory(data -> {
                String value = data.getValue().lastSaleAt();
                return new SimpleStringProperty(value == null || value.isBlank() ? "-" : value);
            });
        }
        if (colDeadStockDaysStagnant != null) {
            colDeadStockDaysStagnant.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().daysSinceLastMovement()));
        }
        if (colDeadStockValue != null) {
            colDeadStockValue.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().deadStockValue())));
        }
        deadStockTable.setItems(FXCollections.observableArrayList());
    }

    private void updateDeadStockPanel(List<MedicineDAO.DeadStockInsightRow> rows) {
        List<MedicineDAO.DeadStockInsightRow> safeRows = rows == null ? List.of() : rows;
        if (deadStockTable != null) {
            deadStockTable.setItems(FXCollections.observableArrayList(safeRows));
        }
        if (lblDeadStockWindow != null) {
            lblDeadStockWindow.setText("No movement threshold: " + DEAD_STOCK_NO_MOVEMENT_DAYS + " days");
        }
        if (lblDeadStockSkuCount != null) {
            lblDeadStockSkuCount.setText(String.valueOf(safeRows.size()));
        }
        if (lblDeadStockAvgDaysStagnant != null) {
            double avgDays = safeRows.stream()
                    .mapToLong(MedicineDAO.DeadStockInsightRow::daysSinceLastMovement)
                    .average()
                    .orElse(0.0);
            lblDeadStockAvgDaysStagnant.setText(String.format("%.1f", avgDays));
        }
        if (lblDeadStockValue != null) {
            double totalValue = safeRows.stream()
                    .mapToDouble(MedicineDAO.DeadStockInsightRow::deadStockValue)
                    .sum();
            lblDeadStockValue.setText(String.format("₹%.2f", totalValue));
        }
    }

    private void setupFastMovingTable() {
        if (fastMovingTable == null) {
            return;
        }
        if (colFastMovingMedicine != null) {
            colFastMovingMedicine.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicineName()));
        }
        if (colFastMovingCompany != null) {
            colFastMovingCompany.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().company()));
        }
        if (colFastMovingUnitsSold != null) {
            colFastMovingUnitsSold.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("%.2f", data.getValue().lookbackUnitsSold())));
        }
        if (colFastMovingRevenue != null) {
            colFastMovingRevenue.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().lookbackRevenue())));
        }
        if (colFastMovingAvgDailyUnits != null) {
            colFastMovingAvgDailyUnits.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("%.2f", data.getValue().averageDailyUnits())));
        }
        if (colFastMovingAvgDailyRevenue != null) {
            colFastMovingAvgDailyRevenue.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().averageDailyRevenue())));
        }
        if (colFastMovingLastSale != null) {
            colFastMovingLastSale.setCellValueFactory(data -> {
                String value = data.getValue().lastSaleAt();
                return new SimpleStringProperty(value == null || value.isBlank() ? "-" : value);
            });
        }
        fastMovingTable.setItems(FXCollections.observableArrayList());
    }

    private void updateFastMovingPanel(List<MedicineDAO.FastMovingInsightRow> rows) {
        List<MedicineDAO.FastMovingInsightRow> safeRows = rows == null ? List.of() : rows;
        if (fastMovingTable != null) {
            fastMovingTable.setItems(FXCollections.observableArrayList(safeRows));
        }
        if (lblFastMovingWindow != null) {
            lblFastMovingWindow.setText(
                    "Range: " + activeFilterStartDate + " to " + activeFilterEndDate + activeFilterScopeText());
        }
        if (lblFastMovingSkuCount != null) {
            lblFastMovingSkuCount.setText(String.valueOf(safeRows.size()));
        }
        if (lblFastMovingUnits != null) {
            double totalUnits = safeRows.stream()
                    .mapToDouble(MedicineDAO.FastMovingInsightRow::lookbackUnitsSold)
                    .sum();
            lblFastMovingUnits.setText(String.format("%.2f", totalUnits));
        }
        if (lblFastMovingRevenue != null) {
            double totalRevenue = safeRows.stream()
                    .mapToDouble(MedicineDAO.FastMovingInsightRow::lookbackRevenue)
                    .sum();
            lblFastMovingRevenue.setText(String.format("₹%.2f", totalRevenue));
        }
    }

    private void setupReturnDamagedTable() {
        if (returnDamagedTable == null) {
            return;
        }
        if (colReturnDamagedMedicine != null) {
            colReturnDamagedMedicine.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicineName()));
        }
        if (colReturnDamagedCompany != null) {
            colReturnDamagedCompany.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().company()));
        }
        if (colReturnDamagedReturnedQty != null) {
            colReturnDamagedReturnedQty.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().returnedQuantity()));
        }
        if (colReturnDamagedDamagedQty != null) {
            colReturnDamagedDamagedQty.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().damagedQuantity()));
        }
        if (colReturnDamagedTotalQty != null) {
            colReturnDamagedTotalQty.setCellValueFactory(
                    data -> new SimpleLongProperty(data.getValue().totalQuantity()));
        }
        if (colReturnDamagedTotalValue != null) {
            colReturnDamagedTotalValue.setCellValueFactory(
                    data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().totalValue())));
        }
        if (colReturnDamagedRootCauseTags != null) {
            colReturnDamagedRootCauseTags.setCellValueFactory(
                    data -> new SimpleStringProperty(data.getValue().rootCauseTags()));
        }
        returnDamagedTable.setItems(FXCollections.observableArrayList());
    }

    private void setupInsightDrillDowns() {
        configureInsightDrillDown(outOfStockTable, MedicineDAO.OutOfStockInsightRow::medicineId, "Out of Stock");
        configureInsightDrillDown(nearStockOutTable, MedicineDAO.NearStockOutInsightRow::medicineId, "Near Stock-Out");
        configureInsightDrillDown(deadStockTable, MedicineDAO.DeadStockInsightRow::medicineId, "Dead Stock");
        configureInsightDrillDown(fastMovingTable, MedicineDAO.FastMovingInsightRow::medicineId, "Fast-Moving");
        configureInsightDrillDown(returnDamagedTable, MedicineDAO.ReturnDamagedInsightRow::medicineId, "Return/Damaged");
    }

    private <T> void configureInsightDrillDown(
            TableView<T> table,
            ToIntFunction<T> medicineIdResolver,
            String sourcePanel) {
        if (table == null || medicineIdResolver == null) {
            return;
        }
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2 || table.getSelectionModel().isEmpty()) {
                return;
            }
            T selectedRow = table.getSelectionModel().getSelectedItem();
            if (selectedRow == null) {
                return;
            }
            int medicineId = medicineIdResolver.applyAsInt(selectedRow);
            if (medicineId <= 0) {
                return;
            }
            showMedicineBatchDrillDown(medicineId, sourcePanel);
        });
    }

    private void showMedicineBatchDrillDown(int medicineId, String sourcePanel) {
        Medicine medicine = masterInventoryList.stream()
                .filter(row -> row.getId() == medicineId)
                .findFirst()
                .orElseGet(() -> medicineDAO.getMedicineById(medicineId));
        if (medicine == null) {
            showAlert(Alert.AlertType.WARNING, "Details Unavailable", "Could not load medicine details for drill-down.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Medicine Drill-Down");
        dialog.setHeaderText("Panel: " + sourcePanel + " | Medicine: "
                + medicine.getName() + " (#" + medicine.getId() + ")");
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(760);

        GridPane medicineGrid = new GridPane();
        medicineGrid.setHgap(16);
        medicineGrid.setVgap(8);
        medicineGrid.setPadding(new javafx.geometry.Insets(10, 0, 10, 0));

        String category = medicine.getGenericName() == null || medicine.getGenericName().isBlank()
                ? "-"
                : medicine.getGenericName();
        String expiry = medicine.getExpiry() == null || medicine.getExpiry().isBlank()
                ? "-"
                : medicine.getExpiry();

        int row = 0;
        medicineGrid.add(new Label("Medicine ID"), 0, row);
        medicineGrid.add(new Label(String.valueOf(medicine.getId())), 1, row++);
        medicineGrid.add(new Label("Name"), 0, row);
        medicineGrid.add(new Label(medicine.getName()), 1, row++);
        medicineGrid.add(new Label("Category"), 0, row);
        medicineGrid.add(new Label(category), 1, row++);
        medicineGrid.add(new Label("Supplier"), 0, row);
        medicineGrid.add(new Label(medicine.getCompany()), 1, row++);
        medicineGrid.add(new Label("Current Stock"), 0, row);
        medicineGrid.add(new Label(String.valueOf(medicine.getStock())), 1, row++);
        medicineGrid.add(new Label("Unit Price"), 0, row);
        medicineGrid.add(new Label(String.format("₹%.2f", medicine.getPrice())), 1, row++);

        String batchCode = "-".equals(expiry) ? "BATCH-" + medicine.getId() : "EXP-" + expiry;
        double inventoryValue = Math.max(0, medicine.getStock()) * medicine.getPrice();

        TableView<BatchSnapshotRow> batchTable = new TableView<>();
        batchTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        batchTable.setPrefHeight(180);

        TableColumn<BatchSnapshotRow, String> colBatchCode = new TableColumn<>("Batch");
        colBatchCode.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().batchCode()));
        TableColumn<BatchSnapshotRow, String> colBatchExpiry = new TableColumn<>("Expiry Date");
        colBatchExpiry.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().expiryDate()));
        TableColumn<BatchSnapshotRow, Number> colBatchStock = new TableColumn<>("Stock Units");
        colBatchStock.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().stockUnits()));
        TableColumn<BatchSnapshotRow, String> colBatchUnitPrice = new TableColumn<>("Unit Price");
        colBatchUnitPrice.setCellValueFactory(
                data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().unitPrice())));
        TableColumn<BatchSnapshotRow, String> colBatchValue = new TableColumn<>("Batch Value");
        colBatchValue.setCellValueFactory(
                data -> new SimpleStringProperty(String.format("₹%.2f", data.getValue().inventoryValue())));
        batchTable.getColumns().setAll(colBatchCode, colBatchExpiry, colBatchStock, colBatchUnitPrice, colBatchValue);
        batchTable.setItems(FXCollections.observableArrayList(
                new BatchSnapshotRow(batchCode, expiry, medicine.getStock(), medicine.getPrice(), inventoryValue)));

        Label scopeLabel = new Label("Active analytics range: "
                + activeFilterStartDate + " to " + activeFilterEndDate + activeFilterScopeText());
        scopeLabel.getStyleClass().add("text-muted");
        Label batchNote = new Label("Batch-level details are based on the current inventory snapshot.");
        batchNote.getStyleClass().add("text-muted");

        VBox root = new VBox(10, medicineGrid, scopeLabel, batchTable, batchNote);
        pane.setContent(root);
        dialog.showAndWait();
    }

    private record BatchSnapshotRow(
            String batchCode,
            String expiryDate,
            int stockUnits,
            double unitPrice,
            double inventoryValue) {
    }

    private void updateReturnDamagedPanel(List<MedicineDAO.ReturnDamagedInsightRow> rows) {
        List<MedicineDAO.ReturnDamagedInsightRow> safeRows = rows == null ? List.of() : rows;
        if (returnDamagedTable != null) {
            returnDamagedTable.setItems(FXCollections.observableArrayList(safeRows));
        }
        if (lblReturnDamagedWindow != null) {
            lblReturnDamagedWindow.setText(
                    "Range: " + activeFilterStartDate + " to " + activeFilterEndDate + activeFilterScopeText());
        }
        if (lblReturnDamagedQuantity != null) {
            long totalQty = safeRows.stream()
                    .mapToLong(MedicineDAO.ReturnDamagedInsightRow::totalQuantity)
                    .sum();
            lblReturnDamagedQuantity.setText(String.valueOf(totalQty));
        }
        if (lblReturnDamagedValue != null) {
            double totalValue = safeRows.stream()
                    .mapToDouble(MedicineDAO.ReturnDamagedInsightRow::totalValue)
                    .sum();
            lblReturnDamagedValue.setText(String.format("₹%.2f", totalValue));
        }
        if (lblReturnDamagedRootCauses != null) {
            long rootCauseCount = safeRows.stream()
                    .flatMap(row -> java.util.Arrays.stream(row.rootCauseTags().split(",")))
                    .map(String::trim)
                    .filter(tag -> !tag.isBlank())
                    .filter(tag -> !"-".equals(tag))
                    .distinct()
                    .count();
            lblReturnDamagedRootCauses.setText(String.valueOf(rootCauseCount));
        }
    }

    private void updateSalesAndMarginPanel(BillDAO.WeeklySalesMarginSummary summary) {
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(LocalDate.now());
        BillDAO.WeeklySalesMarginSummary safeSummary = summary == null
                ? new BillDAO.WeeklySalesMarginSummary(
                        weeklyWindow.startDate().toString(),
                        weeklyWindow.endDate().toString(),
                        0L,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0)
                : summary;

        if (lblSalesMarginWindow != null) {
            lblSalesMarginWindow.setText("Week: " + safeSummary.weekStartDate() + " to " + safeSummary.weekEndDate());
        }
        if (lblSalesMarginGrossSales != null) {
            lblSalesMarginGrossSales.setText(String.format("₹%.2f", safeSummary.grossSales()));
        }
        if (lblSalesMarginNetSales != null) {
            lblSalesMarginNetSales.setText(String.format("₹%.2f", safeSummary.netSales()));
        }
        if (lblSalesMarginGrossMargin != null) {
            lblSalesMarginGrossMargin.setText(
                    String.format("₹%.2f (%.2f%%)", safeSummary.grossMargin(), safeSummary.grossMarginPercent()));
        }
        if (lblSalesMarginDiscountBurn != null) {
            lblSalesMarginDiscountBurn.setText(
                    String.format("₹%.2f | Bills: %d", safeSummary.discountBurn(), safeSummary.billCount()));
        }
    }

    private void updateSubscriptionImpactPanel(SubscriptionDAO.WeeklyAnalyticsSummaryRow summary) {
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(LocalDate.now());
        SubscriptionDAO.WeeklyAnalyticsSummaryRow safeSummary = summary == null
                ? new SubscriptionDAO.WeeklyAnalyticsSummaryRow(
                        weeklyWindow.startDate().toString(),
                        weeklyWindow.endDate().toString(),
                        ZoneId.systemDefault().getId(),
                        0L,
                        0L,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        null)
                : summary;

        if (lblSubscriptionImpactWindow != null) {
            lblSubscriptionImpactWindow.setText(
                    "Week: " + safeSummary.weekStartDate() + " to " + safeSummary.weekEndDate()
                            + " (" + safeSummary.timezoneName() + ")");
        }
        if (lblSubscriptionImpactMembersBilled != null) {
            lblSubscriptionImpactMembersBilled.setText(String.valueOf(safeSummary.subscriptionBillCount()));
        }
        if (lblSubscriptionImpactSavingsGiven != null) {
            lblSubscriptionImpactSavingsGiven.setText(String.format("₹%.2f", safeSummary.totalSavings()));
        }
        if (lblSubscriptionImpactRenewalsDue != null) {
            lblSubscriptionImpactRenewalsDue.setText(String.valueOf(safeSummary.renewalsDueNext7Days()));
        }
        if (lblSubscriptionImpactOverrideCount != null) {
            lblSubscriptionImpactOverrideCount.setText(String.valueOf(safeSummary.pendingOverrideCount()));
        }
    }

    private void setupAnomalyAlertsTable() {
        if (anomalyAlertsTable == null) {
            return;
        }
        if (colAnomalyType != null) {
            colAnomalyType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().alertType()));
        }
        if (colAnomalySeverity != null) {
            colAnomalySeverity.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().severity()));
        }
        if (colAnomalyMetric != null) {
            colAnomalyMetric.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().metricValue()));
        }
        if (colAnomalyThreshold != null) {
            colAnomalyThreshold.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().thresholdRule()));
        }
        if (colAnomalyMessage != null) {
            colAnomalyMessage.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message()));
        }
        anomalyAlertsTable.setItems(FXCollections.observableArrayList());
    }

    private void updateAnomalyAlertsPanel(List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts) {
        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> safeAlerts = alerts == null ? List.of() : alerts;
        if (anomalyAlertsTable != null) {
            anomalyAlertsTable.setItems(FXCollections.observableArrayList(safeAlerts));
        }
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(LocalDate.now());
        if (lblAnomalyWindow != null) {
            lblAnomalyWindow.setText("Week: " + weeklyWindow.startDate() + " to " + weeklyWindow.endDate());
        }
        if (lblAnomalyCount != null) {
            lblAnomalyCount.setText(String.valueOf(safeAlerts.size()));
        }
        if (lblAnomalyHighCount != null) {
            long highCount = safeAlerts.stream()
                    .filter(alert -> "HIGH".equalsIgnoreCase(alert.severity()))
                    .count();
            lblAnomalyHighCount.setText(String.valueOf(highCount));
        }
    }

    private void setupActionTrackerTable() {
        if (actionTrackerTable == null) {
            return;
        }
        if (colActionTrackerAlertType != null) {
            colActionTrackerAlertType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().alertType()));
        }
        if (colActionTrackerSeverity != null) {
            colActionTrackerSeverity.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().severity()));
        }
        if (colActionTrackerOwner != null) {
            colActionTrackerOwner.setCellValueFactory(data -> {
                String owner = data.getValue().ownerUsername();
                if (owner == null || owner.isBlank()) {
                    owner = data.getValue().ownerUserId() == null ? "-" : "User #" + data.getValue().ownerUserId();
                }
                return new SimpleStringProperty(owner);
            });
        }
        if (colActionTrackerDueDate != null) {
            colActionTrackerDueDate.setCellValueFactory(data -> {
                String value = data.getValue().dueDate();
                return new SimpleStringProperty(value == null || value.isBlank() ? "-" : value);
            });
        }
        if (colActionTrackerStatus != null) {
            colActionTrackerStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().closureStatus()));
        }
        actionTrackerTable.setItems(FXCollections.observableArrayList());

        if (cmbActionClosureStatus != null) {
            cmbActionClosureStatus.setItems(FXCollections.observableArrayList("OPEN", "IN_PROGRESS", "CLOSED"));
        }
        actionTrackerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (newRow == null) {
                return;
            }
            if (cmbActionClosureStatus != null) {
                cmbActionClosureStatus.setValue(newRow.closureStatus());
            }
            if (dpActionDueDate != null) {
                dpActionDueDate.setValue(parseLocalDateSafe(newRow.dueDate()));
            }
        });
    }

    private void updateActionTrackerPanel(List<AnomalyActionTrackerDAO.ActionTrackerRow> rows) {
        List<AnomalyActionTrackerDAO.ActionTrackerRow> safeRows = rows == null ? List.of() : rows;
        if (actionTrackerTable != null) {
            actionTrackerTable.setItems(FXCollections.observableArrayList(safeRows));
        }
        if (lblActionTrackerCount != null) {
            lblActionTrackerCount.setText(String.valueOf(safeRows.size()));
        }
        if (lblActionTrackerClosedCount != null) {
            long closed = safeRows.stream()
                    .filter(row -> "CLOSED".equalsIgnoreCase(row.closureStatus()))
                    .count();
            lblActionTrackerClosedCount.setText(String.valueOf(closed));
        }
    }

    private Integer resolveDefaultActionOwnerUserId() {
        if (userSession == null || !userSession.isLoggedIn() || userSession.getUser() == null) {
            return null;
        }
        var user = userSession.getUser();
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER) {
            return user.getId();
        }
        return null;
    }

    private LocalDate parseLocalDateSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().length() > 10 ? value.trim().substring(0, 10) : value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    @FXML
    private void handleSaveActionTracker() {
        if (actionTrackerTable == null) {
            return;
        }
        AnomalyActionTrackerDAO.ActionTrackerRow selected = actionTrackerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Action Selected", "Select an action tracker row first.");
            return;
        }
        Integer ownerUserId = resolveDefaultActionOwnerUserId();
        if (ownerUserId == null) {
            ownerUserId = selected.ownerUserId();
        }
        String status = cmbActionClosureStatus == null ? selected.closureStatus() : cmbActionClosureStatus.getValue();
        if (status == null || status.isBlank()) {
            status = selected.closureStatus();
        }
        LocalDate dueDate = dpActionDueDate == null ? parseLocalDateSafe(selected.dueDate()) : dpActionDueDate.getValue();

        boolean updated = anomalyActionTrackerDAO.updateAction(selected.actionId(), ownerUserId, dueDate, status);
        if (!updated) {
            showAlert(Alert.AlertType.ERROR, "Update Failed", "Could not update the selected action.");
            return;
        }

        List<AnomalyActionTrackerDAO.ActionTrackerRow> refreshed = anomalyActionTrackerDAO.getWeeklyActions(
                LocalDate.now(),
                ZoneId.systemDefault().getId(),
                ACTION_TRACKER_MAX_ROWS);
        updateActionTrackerPanel(refreshed);
        showAlert(Alert.AlertType.INFORMATION, "Action Updated", "Tracker row updated successfully.");
    }

    // ======================== EXPIRY ALERTS ========================

    private void checkExpiryAlerts() {
        if (mainTabPane != null) {
            mainTabPane.getTabs().removeIf(tab -> "Expiry Alerts".equals(tab.getText()));
        }
        Tab expiryTab = new Tab("Expiry Alerts");
        expiryTab.setClosable(false);

        TableView<Medicine> expiryTable = new TableView<>();

        TableColumn<Medicine, String> colMed = new TableColumn<>("Medicine");
        colMed.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<Medicine, String> colExp = new TableColumn<>("Expiry Date");
        colExp.setCellValueFactory(data -> data.getValue().expiryProperty());

        expiryTable.getColumns().setAll(java.util.List.of(colMed, colExp));

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate warningDate = today.plusDays(30);

        List<Medicine> expiring = new ArrayList<>();
        for (Medicine m : masterInventoryList) {
            try {
                String expStr = m.getExpiry();
                if (expStr != null && expStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    java.time.LocalDate exp = java.time.LocalDate.parse(expStr);
                    if (!exp.isAfter(warningDate)) {
                        expiring.add(m);
                    }
                }
            } catch (Exception e) {
                // ignore parse error
            }
        }

        if (!expiring.isEmpty()) {
            expiryTable.setItems(FXCollections.observableArrayList(expiring));
            expiryTab.getStyleClass().add("expiry-alert-tab");
            expiryTab.setContent(expiryTable);
            mainTabPane.getTabs().add(expiryTab);
        }
    }

    // ======================== HISTORY ========================

    private void setupHistoryTable() {
        histColId.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getBillId()).asObject());
        histColDate.setCellValueFactory(data -> data.getValue().dateProperty());
        histColCustomer.setCellValueFactory(data -> data.getValue().customerNameProperty());
        histColPhone.setCellValueFactory(data -> data.getValue().phoneProperty());
        histColAmount.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        historyTable.setItems(historyList);

        // Double-click for bill details
        historyTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !historyTable.getSelectionModel().isEmpty()) {
                showBillDetails(historyTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void loadHistory() {
        loadHistoryPage(0);
    }

    private void showBillDetails(BillHistoryRecord bill) {
        if (bill == null)
            return;

        List<BillItem> items = billDAO.getBillItemsExtended(bill.getBillId());

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Bill Details - #" + bill.getBillId());
        dialog.setHeaderText("Customer: " + bill.customerNameProperty().get() + "\n" +
                "Phone: " + bill.phoneProperty().get() + "\n" +
                "Date: " + bill.dateProperty().get());

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(600);

        TableView<BillItem> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(items));

        TableColumn<BillItem, String> colName = new TableColumn<>("Medicine");
        colName.setCellValueFactory(d -> d.getValue().nameProperty());
        colName.setPrefWidth(150);

        TableColumn<BillItem, String> colExpiry = new TableColumn<>("Expiry");
        colExpiry.setCellValueFactory(d -> d.getValue().expiryProperty());

        TableColumn<BillItem, Integer> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(d -> d.getValue().qtyProperty().asObject());

        TableColumn<BillItem, Double> colPr = new TableColumn<>("Price");
        colPr.setCellValueFactory(d -> d.getValue().priceProperty().asObject());

        TableColumn<BillItem, Double> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(d -> d.getValue().totalProperty().asObject());

        table.getColumns().setAll(java.util.List.of(colName, colExpiry, colQty, colPr, colTotal));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(300);

        // Download button
        Button btnDownload = new Button("Download Invoice");
        btnDownload.getStyleClass().add("button-primary");
        btnDownload.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Invoice PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName("Invoice_" + bill.getBillId() + ".pdf");
            File file = fileChooser.showSaveDialog(dialog.getOwner());

            if (file != null) {
                try {
                    reportService.generateInvoicePDF(items, bill.totalProperty().get(),
                            bill.customerNameProperty().get(), file.getAbsolutePath());
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice saved.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to save PDF: " + ex.getMessage());
                }
            }
        });

        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10, btnDownload);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new javafx.geometry.Insets(10, 0, 0, 0));

        VBox root = new VBox(10, table, buttonBox);
        pane.setContent(root);
        dialog.showAndWait();
    }

    // ======================== BUSINESS INTELLIGENCE (AI) ========================

    @FXML
    private void handleFindSubstitutes() {
        String brand = substituteInput.getText().trim();
        if (brand.isEmpty()) {
            substituteResult.setText("Please enter a brand name.");
            return;
        }
        substituteResult.setText("Analyzing '" + brand + "' with AI and checking inventory...");
        inventoryAIService.findSubstitutes(brand)
                .thenAccept(result -> Platform.runLater(() -> substituteResult.setText(result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> substituteResult.setText("Error: " + ex.getMessage()));
                    return null;
                });
    }

    @FXML
    private void handleRestockReport() {
        forecastResult.setText("Analyzing sales history (last 30 days) for trends...");
        inventoryAIService.generateRestockReport()
                .thenAccept(result -> Platform.runLater(() -> forecastResult.setText(result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> forecastResult.setText("Error: " + ex.getMessage()));
                    return null;
                });
    }

    @FXML
    private void handleExpiryReport() {
        expiryResult.setText("Scanning inventory for expiring items and generating strategy...");
        inventoryAIService.generateExpiryReport()
                .thenAccept(result -> Platform.runLater(() -> expiryResult.setText(result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> expiryResult.setText("Error: " + ex.getMessage()));
                    return null;
                });
    }

    // ======================== EXPORT ========================

    @FXML
    private void handleExportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Inventory Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("Inventory_" + java.time.LocalDate.now() + ".xlsx");
        File file = fileChooser.showSaveDialog(inventoryTable.getScene().getWindow());

        if (file != null) {
            try {
                reportService.exportInventoryToExcel(new ArrayList<>(masterInventoryList), file.getAbsolutePath());
                showAlert(Alert.AlertType.INFORMATION, "Success", "Inventory exported successfully!");
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            }
        }
    }

    // ======================== EXPENSES ========================

    private void setupExpenseTab() {
        Tab expenseTab = new Tab("Expenses");
        expenseTab.setClosable(false);

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(15));

        Button btnAdd = new Button("Record New Expense");
        btnAdd.setOnAction(e -> handleAddExpense());

        Label lblMonthly = new Label("Monthly Expenses: calculating...");

        content.getChildren().addAll(new Label("Expense Manager"), btnAdd, lblMonthly);

        expenseTab.setContent(content);
        mainTabPane.getTabs().add(expenseTab);
    }

    private void handleAddExpense() {
        Dialog<Expense> dialog = new Dialog<>();
        dialog.setTitle("Add Expense");
        dialog.setHeaderText("Record a new shop expense");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField category = new TextField();
        category.setPromptText("Rent, Salary, etc.");
        TextField amount = new TextField();
        amount.setPromptText("0.00");
        TextField description = new TextField();
        description.setPromptText("Optional details");

        grid.add(new Label("Category:"), 0, 0);
        grid.add(category, 1, 0);
        grid.add(new Label("Amount:"), 0, 1);
        grid.add(amount, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(description, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    return new Expense(0, category.getText(), Double.parseDouble(amount.getText()),
                            java.time.LocalDate.now().toString(), description.getText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        java.util.Optional<Expense> result = dialog.showAndWait();
        result.ifPresent(exp -> {
            try {
                expenseDAO.addExpense(exp.getCategory(), exp.getAmount(), exp.getDate(), exp.getDescription());
                DashboardKpiService.invalidateExpenseMetrics();
                loadKPIs();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Expense added.");
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to save expense.");
            }
        });
    }

    // ======================== UTILS ========================

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleHistoryPrevPage() {
        if (historyPageIndex > 0) {
            loadHistoryPage(historyPageIndex - 1);
        }
    }

    @FXML
    private void handleHistoryNextPage() {
        int totalPages = Math.max(1, (int) Math.ceil(historyTotalCount / (double) HISTORY_PAGE_SIZE));
        if (historyPageIndex + 1 < totalPages) {
            loadHistoryPage(historyPageIndex + 1);
        }
    }

    private void loadHistoryPage(int pageIndex) {
        final int requestedPage = Math.max(0, pageIndex);
        AppExecutors.runBackground(() -> {
            int total = billDAO.countBillHistory();
            int totalPages = Math.max(1, (int) Math.ceil(total / (double) HISTORY_PAGE_SIZE));
            int resolvedPage = Math.min(requestedPage, totalPages - 1);
            int offset = resolvedPage * HISTORY_PAGE_SIZE;

            var history = billDAO.getBillHistoryPage(offset, HISTORY_PAGE_SIZE);

            Platform.runLater(() -> {
                historyPageIndex = resolvedPage;
                historyTotalCount = total;
                historyList.setAll(history);
                updateHistoryPagingControls();
            });
        });
    }

    private void updateHistoryPagingControls() {
        int totalPages = Math.max(1, (int) Math.ceil(historyTotalCount / (double) HISTORY_PAGE_SIZE));
        int start = historyTotalCount == 0 ? 0 : (historyPageIndex * HISTORY_PAGE_SIZE) + 1;
        int end = historyTotalCount == 0 ? 0 : Math.min(historyTotalCount, (historyPageIndex + 1) * HISTORY_PAGE_SIZE);

        if (lblHistoryPageInfo != null) {
            lblHistoryPageInfo.setText(String.format("Rows %d-%d of %d (Page %d/%d)",
                    start, end, historyTotalCount, historyPageIndex + 1, totalPages));
        }
        if (btnHistoryPrev != null) {
            btnHistoryPrev.setDisable(historyPageIndex <= 0);
        }
        if (btnHistoryNext != null) {
            btnHistoryNext.setDisable(historyPageIndex + 1 >= totalPages);
        }
    }
}
