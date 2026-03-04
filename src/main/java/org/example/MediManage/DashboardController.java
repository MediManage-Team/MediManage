package org.example.MediManage;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.BillHistoryRecord;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.Expense;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;

import org.example.MediManage.service.ReportService;
import org.example.MediManage.service.DashboardKpiService;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Pos;
import javafx.application.Platform;
import org.example.MediManage.util.AppExecutors;

/**
 * Simplified bento-box DashboardController — KPIs, stock health,
 * sales summary, inventory, history, BI, expenses.
 */
public class DashboardController {

    // ── KPI LABELS ──
    @FXML
    private Label dailySales;
    @FXML
    private Label lowStock;
    @FXML
    private Label pendingRx;
    @FXML
    private Label totalProfit;
    // ── TODAY'S SUMMARY LABELS ──
    @FXML
    private Label lblTodayBills;
    @FXML
    private Label lblTodayCustomers;
    @FXML
    private Label lblMonthlyExpenses;
    @FXML
    private Label lblTodayReturns;
    @FXML
    private Label avgMargin;
    @FXML
    private Label lblReorderNeeded;

    // ── EXPIRY LABELS ──
    @FXML
    private Label lblExpiryExpired;
    @FXML
    private Label lblExpiry0To30;
    @FXML
    private Label lblExpiry31To60;
    @FXML
    private Label lblExpiry61To90;

    // ── SALES & MARGIN LABELS ──
    @FXML
    private Label lblGrossSales;
    @FXML
    private Label lblNetSales;
    @FXML
    private Label lblGrossMargin;

    // ── STOCK HEALTH TABLE ──
    @FXML
    private TableView<StockHealthRow> stockHealthTable;
    @FXML
    private TableColumn<StockHealthRow, String> colStockHealthMedicine;
    @FXML
    private TableColumn<StockHealthRow, String> colStockHealthCompany;
    @FXML
    private TableColumn<StockHealthRow, String> colStockHealthIssue;
    @FXML
    private TableColumn<StockHealthRow, Number> colStockHealthStock;
    @FXML
    private TableColumn<StockHealthRow, String> colStockHealthDetail;

    // ── TOP MOVERS TABLE ──
    @FXML
    private TableView<MedicineDAO.FastMovingInsightRow> topMoversTable;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colTopMoverMedicine;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, Number> colTopMoverUnitsSold;
    @FXML
    private TableColumn<MedicineDAO.FastMovingInsightRow, String> colTopMoverRevenue;

    // ── INVENTORY TABLE ──
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
    private TableColumn<Medicine, String> colMargin;
    @FXML
    private TextField searchMedicine;

    // ── HISTORY TABLE ──
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

    // ── BI ──
    @FXML
    private TextField substituteInput;
    @FXML
    private TextArea substituteResult;
    @FXML
    private TextArea forecastResult;
    @FXML
    private TextArea expiryResult;

    // ── TABS ──
    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab aiTab;

    // ── DATA ──
    private final ObservableList<Medicine> masterInventoryList = FXCollections.observableArrayList();
    private final ObservableList<BillHistoryRecord> historyList = FXCollections.observableArrayList();
    private static final int HISTORY_PAGE_SIZE = 50;
    private int historyPageIndex = 0;
    private int historyTotalCount = 0;

    // ── SERVICES ──
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final BillDAO billDAO = new BillDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();

    private final DashboardKpiService kpiService = DashboardKpiService.getInstance();
    private final ReportService reportService = new ReportService();
    private org.example.MediManage.service.ai.InventoryAIService inventoryAIService;

    // ══════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void initialize() {
        inventoryAIService = new org.example.MediManage.service.ai.InventoryAIService();

        setupInventoryTable();
        setupStockHealthTable();
        setupTopMoversTable();
        setupHistoryTable();

        loadDashboard();
        loadHistory();
        setupExpenseTab();
    }

    /**
     * Single method to load all dashboard data in one background call.
     */
    private void loadDashboard() {
        AppExecutors.runBackground(() -> {
            var allMedicines = medicineDAO.getAllMedicines();

            // KPIs
            DashboardKpiService.DashboardKpis kpis = kpiService.getDashboardKpis(allMedicines);

            // Sales & margin (current week)
            BillDAO.WeeklySalesMarginSummary salesSummary = kpiService.getWeeklySalesSummary();

            // Stock health issues (combined low stock + expiring)
            List<StockHealthRow> stockIssues = buildStockHealthRows(allMedicines);

            // Top movers
            List<MedicineDAO.FastMovingInsightRow> topMovers = kpiService.getTopMovers(30, 5);

            // Expiry alerts
            List<Medicine> expiring = findExpiringMedicines(allMedicines);

            // Reorder needed count
            int reorderCount = medicineDAO.getReorderNeeded().size();

            Platform.runLater(() -> {
                masterInventoryList.setAll(allMedicines);

                // KPI cards
                dailySales.setText(String.format("₹%.2f", kpis.dailySales()));
                if (lowStock != null)
                    lowStock.setText(String.valueOf(kpis.lowStockCount()));
                if (totalProfit != null)
                    totalProfit.setText(String.format("₹%.2f", kpis.netProfit()));
                if (pendingRx != null)
                    pendingRx.setText(String.valueOf(kpis.pendingRxCount()));
                if (avgMargin != null)
                    avgMargin.setText(String.format("%.1f%%", kpis.avgProfitMargin()));

                // Today's Summary
                if (lblTodayBills != null)
                    lblTodayBills.setText(String.valueOf(kpis.dailyBillCount()));
                if (lblTodayCustomers != null)
                    lblTodayCustomers.setText(String.valueOf(kpis.dailyCustomerCount()));
                if (lblMonthlyExpenses != null)
                    lblMonthlyExpenses.setText(String.format("₹%.2f", kpis.monthlyExpenses()));
                if (lblTodayReturns != null)
                    lblTodayReturns.setText("0");
                if (lblReorderNeeded != null)
                    lblReorderNeeded.setText(String.valueOf(reorderCount));

                // Expiry buckets
                if (lblExpiryExpired != null)
                    lblExpiryExpired.setText(String.valueOf(kpis.expiredMedicinesCount()));
                if (lblExpiry0To30 != null)
                    lblExpiry0To30.setText(String.valueOf(kpis.expiry0To30DaysCount()));
                if (lblExpiry31To60 != null)
                    lblExpiry31To60.setText(String.valueOf(kpis.expiry31To60DaysCount()));
                if (lblExpiry61To90 != null)
                    lblExpiry61To90.setText(String.valueOf(kpis.expiry61To90DaysCount()));

                // Sales & margin
                if (salesSummary != null) {
                    if (lblGrossSales != null)
                        lblGrossSales.setText(String.format("₹%.2f", salesSummary.netSales()));
                    if (lblNetSales != null)
                        lblNetSales.setText(String.format("₹%.2f", salesSummary.netSales()));
                    if (lblGrossMargin != null)
                        lblGrossMargin.setText(String.format("%.1f%%", salesSummary.grossMarginPercent()));
                }

                // Stock health table
                if (stockHealthTable != null)
                    stockHealthTable.setItems(FXCollections.observableArrayList(stockIssues));

                // Top movers
                if (topMoversTable != null)
                    topMoversTable.setItems(FXCollections.observableArrayList(topMovers));

                // Expiry alerts tab
                showExpiryAlertsTab(expiring);
            });
        });
    }

    // ══════════════════════════════════════════════════════════════
    // STOCK HEALTH (combined low-stock + expiring + dead)
    // ══════════════════════════════════════════════════════════════

    public record StockHealthRow(String medicine, String company, String issue, int stock, String detail) {
    }

    private void setupStockHealthTable() {
        if (stockHealthTable == null)
            return;
        if (colStockHealthMedicine != null)
            colStockHealthMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicine()));
        if (colStockHealthCompany != null)
            colStockHealthCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().company()));
        if (colStockHealthIssue != null)
            colStockHealthIssue.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().issue()));
        if (colStockHealthStock != null)
            colStockHealthStock.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().stock()));
        if (colStockHealthDetail != null)
            colStockHealthDetail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().detail()));
        stockHealthTable.setItems(FXCollections.observableArrayList());
    }

    private List<StockHealthRow> buildStockHealthRows(List<Medicine> medicines) {
        List<StockHealthRow> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate warningDate = today.plusDays(30);

        for (Medicine m : medicines) {
            // Low stock
            if (m.getStock() > 0 && m.getStock() <= 10) {
                rows.add(new StockHealthRow(
                        m.getName(), m.getCompany(), "Low Stock",
                        m.getStock(), m.getStock() + " units left"));
            }
            // Out of stock
            if (m.getStock() == 0) {
                rows.add(new StockHealthRow(
                        m.getName(), m.getCompany(), "Out of Stock",
                        0, "Needs reorder"));
            }
            // Expiring soon
            try {
                String expStr = m.getExpiry();
                if (expStr != null && expStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    LocalDate exp = LocalDate.parse(expStr);
                    if (exp.isBefore(today)) {
                        rows.add(new StockHealthRow(
                                m.getName(), m.getCompany(), "Expired",
                                m.getStock(), "Expired " + expStr));
                    } else if (!exp.isAfter(warningDate)) {
                        long daysLeft = today.until(exp).getDays();
                        rows.add(new StockHealthRow(
                                m.getName(), m.getCompany(), "Expiring",
                                m.getStock(), daysLeft + " days left"));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Sort by severity: Expired > Out of Stock > Expiring > Low Stock, limit to 15
        rows.sort((a, b) -> {
            int priority = issuePriority(a.issue()) - issuePriority(b.issue());
            return priority != 0 ? priority : a.medicine().compareTo(b.medicine());
        });
        return rows.size() > 15 ? rows.subList(0, 15) : rows;
    }

    private int issuePriority(String issue) {
        return switch (issue) {
            case "Expired" -> 0;
            case "Out of Stock" -> 1;
            case "Expiring" -> 2;
            case "Low Stock" -> 3;
            default -> 4;
        };
    }

    // ══════════════════════════════════════════════════════════════
    // TOP MOVERS TABLE
    // ══════════════════════════════════════════════════════════════

    private void setupTopMoversTable() {
        if (topMoversTable == null)
            return;
        if (colTopMoverMedicine != null)
            colTopMoverMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        if (colTopMoverUnitsSold != null)
            colTopMoverUnitsSold.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().lookbackUnitsSold()));
        if (colTopMoverRevenue != null)
            colTopMoverRevenue.setCellValueFactory(
                    d -> new SimpleStringProperty(String.format("₹%.2f", d.getValue().lookbackRevenue())));
        topMoversTable.setItems(FXCollections.observableArrayList());
    }

    // ══════════════════════════════════════════════════════════════
    // INVENTORY TABLE
    // ══════════════════════════════════════════════════════════════

    private void setupInventoryTable() {
        colMedicine.setCellValueFactory(data -> data.getValue().nameProperty());
        colCompany.setCellValueFactory(data -> data.getValue().companyProperty());
        colExpiry.setCellValueFactory(data -> data.getValue().expiryProperty());
        colStock.setCellValueFactory(data -> data.getValue().stockProperty().asObject());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());
        if (colMargin != null) {
            colMargin.setCellValueFactory(data -> {
                double margin = data.getValue().getProfitMarginPercent();
                return new SimpleStringProperty(margin > 0 ? String.format("%.1f%%", margin) : "-");
            });
        }

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

    // ══════════════════════════════════════════════════════════════
    // EXPIRY ALERTS TAB (auto-generated)
    // ══════════════════════════════════════════════════════════════

    private List<Medicine> findExpiringMedicines(List<Medicine> medicines) {
        LocalDate warningDate = LocalDate.now().plusDays(30);
        List<Medicine> expiring = new ArrayList<>();
        for (Medicine m : medicines) {
            try {
                String expStr = m.getExpiry();
                if (expStr != null && expStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    LocalDate exp = LocalDate.parse(expStr);
                    if (!exp.isAfter(warningDate)) {
                        expiring.add(m);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return expiring;
    }

    private void showExpiryAlertsTab(List<Medicine> expiring) {
        if (mainTabPane == null)
            return;
        mainTabPane.getTabs().removeIf(tab -> "Expiry Alerts".equals(tab.getText()));

        if (expiring.isEmpty())
            return;

        Tab expiryTab = new Tab("Expiry Alerts");
        expiryTab.setClosable(false);

        TableView<Medicine> expiryTable = new TableView<>();
        TableColumn<Medicine, String> colMed = new TableColumn<>("Medicine");
        colMed.setCellValueFactory(data -> data.getValue().nameProperty());
        TableColumn<Medicine, String> colExp = new TableColumn<>("Expiry Date");
        colExp.setCellValueFactory(data -> data.getValue().expiryProperty());
        expiryTable.getColumns().setAll(List.of(colMed, colExp));
        expiryTable.setItems(FXCollections.observableArrayList(expiring));

        expiryTab.getStyleClass().add("expiry-alert-tab");
        expiryTab.setContent(expiryTable);
        mainTabPane.getTabs().add(expiryTab);
    }

    // ══════════════════════════════════════════════════════════════
    // HISTORY
    // ══════════════════════════════════════════════════════════════

    private void setupHistoryTable() {
        histColId.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getBillId()).asObject());
        histColDate.setCellValueFactory(data -> data.getValue().dateProperty());
        histColCustomer.setCellValueFactory(data -> data.getValue().customerNameProperty());
        histColPhone.setCellValueFactory(data -> data.getValue().phoneProperty());
        histColAmount.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        historyTable.setItems(historyList);

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

        TableColumn<BillItem, String> colBillExpiry = new TableColumn<>("Expiry");
        colBillExpiry.setCellValueFactory(d -> d.getValue().expiryProperty());

        TableColumn<BillItem, Integer> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(d -> d.getValue().qtyProperty().asObject());

        TableColumn<BillItem, Double> colPr = new TableColumn<>("Price");
        colPr.setCellValueFactory(d -> d.getValue().priceProperty().asObject());

        TableColumn<BillItem, Double> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(d -> d.getValue().totalProperty().asObject());

        table.getColumns().setAll(List.of(colName, colBillExpiry, colQty, colPr, colTotal));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(300);

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
        int end = historyTotalCount == 0 ? 0
                : Math.min(historyTotalCount, (historyPageIndex + 1) * HISTORY_PAGE_SIZE);

        if (lblHistoryPageInfo != null)
            lblHistoryPageInfo.setText(String.format("Rows %d-%d of %d (Page %d/%d)",
                    start, end, historyTotalCount, historyPageIndex + 1, totalPages));
        if (btnHistoryPrev != null)
            btnHistoryPrev.setDisable(historyPageIndex <= 0);
        if (btnHistoryNext != null)
            btnHistoryNext.setDisable(historyPageIndex + 1 >= totalPages);
    }

    // ══════════════════════════════════════════════════════════════
    // BUSINESS INTELLIGENCE (AI)
    // ══════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleExportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Inventory Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("Inventory_" + LocalDate.now() + ".xlsx");
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

    // ══════════════════════════════════════════════════════════════
    // EXPENSES (programmatic tab)
    // ══════════════════════════════════════════════════════════════

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
                            LocalDate.now().toString(), description.getText());
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
                loadDashboard();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Expense added.");
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to save expense.");
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    // UTILS
    // ══════════════════════════════════════════════════════════════

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
