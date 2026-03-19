package org.example.MediManage.controller;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.BillHistoryRecord;
import org.example.MediManage.model.Medicine;


import java.io.File;
import java.io.IOException;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.MediManage.service.ReportService;
import org.example.MediManage.service.DashboardKpiService;
import javafx.scene.layout.VBox;

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

    // ── BI TEXT TOOLS (now using popup dialogs via AIResultDialog) ──

    // ── BI CHARTS ──
    @FXML
    private LineChart<String, Number> salesTrendChart;
    @FXML
    private PieChart paymentModeChart;
    @FXML
    private BarChart<String, Number> topProductsChart;
    @FXML
    private PieChart expenseCategoryChart;

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
    private boolean biChartsLoaded = false;
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

        // Lazy-load BI charts when the BI tab is selected
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null && "Business Intelligence".equals(newTab.getText()) && !biChartsLoaded) {
                    loadBICharts();
                    biChartsLoaded = true;
                }
            });
        }
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
                    String storedProtocol = billDAO.getAICareProtocol(bill.getBillId());
                    if (storedProtocol == null) storedProtocol = "";
                    else {
                        storedProtocol = storedProtocol
                            .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                            .replaceAll("(?m)^#+\\s+", "<b>")
                            .replaceAll("(?m)^#+\\s+.*$", "$0</b>")
                            .replaceAll("`", "")
                            .trim();
                    }
                    reportService.generateInvoicePDF(items, bill.totalProperty().get(),
                            bill.customerNameProperty().get(), file.getAbsolutePath(), storedProtocol, bill.getBillId());
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice saved.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to save PDF: " + ex.getMessage());
                }
            }
        });

        Button btnDownloadReceipt = new Button("Download Receipt");
        btnDownloadReceipt.getStyleClass().add("button-secondary");
        btnDownloadReceipt.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Receipt PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName("Receipt_" + bill.getBillId() + ".pdf");
            File file = fileChooser.showSaveDialog(dialog.getOwner());

            if (file != null) {
                try {
                    reportService.generateReceiptPDF(items, bill.totalProperty().get(),
                            bill.customerNameProperty().get(), file.getAbsolutePath(), bill.getBillId());
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Receipt saved.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to save Receipt: " + ex.getMessage());
                }
            }
        });

        Button btnWhatsApp = new Button("WhatsApp Invoice");
        btnWhatsApp.getStyleClass().add("button-primary");
        btnWhatsApp.setOnAction(e -> {
            TextInputDialog phoneDialog = new TextInputDialog(bill.phoneProperty().get());
            phoneDialog.setTitle("Send via WhatsApp");
            phoneDialog.setHeaderText("Enter mobile number to send WhatsApp Invoice:");
            phoneDialog.setContentText("Phone:");
            phoneDialog.showAndWait().ifPresent(phone -> {
                if (!phone.trim().isEmpty()) {
                    try {
                        File tempFile = File.createTempFile("Invoice_" + bill.getBillId(), ".pdf");
                        String storedProtocol = billDAO.getAICareProtocol(bill.getBillId());
                        if (storedProtocol == null) storedProtocol = "";
                        else {
                            storedProtocol = storedProtocol
                                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                                .replaceAll("(?m)^#+\\s+", "<b>")
                                .replaceAll("(?m)^#+\\s+.*$", "$0</b>")
                                .replaceAll("`", "")
                                .trim();
                        }
                        reportService.generateInvoicePDF(items, bill.totalProperty().get(), bill.customerNameProperty().get(), tempFile.getAbsolutePath(), storedProtocol, bill.getBillId());
                        
                        org.example.MediManage.service.WhatsAppService.sendInvoiceWhatsApp(
                                phone.trim(), bill.customerNameProperty().get(), bill.totalProperty().get(),
                                storedProtocol, bill.getBillId(), tempFile.getAbsolutePath()
                        ).thenAccept(res -> Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice sent via WhatsApp!")))
                         .exceptionally(ex -> {
                             Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to send WhatsApp: " + ex.getMessage()));
                             return null;
                         });
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to generate PDF: " + ex.getMessage());
                    }
                }
            });
        });

        Button btnEmail = new Button("Email Invoice");
        btnEmail.getStyleClass().add("button-secondary");
        btnEmail.setOnAction(e -> {
            String defaultEmail = billDAO.getCustomerEmailByBillId(bill.getBillId());
            TextInputDialog emailDialog = new TextInputDialog(defaultEmail);
            emailDialog.setTitle("Send via Email");
            emailDialog.setHeaderText("Enter email address to send Invoice:");
            emailDialog.setContentText("Email:");
            emailDialog.showAndWait().ifPresent(email -> {
                 if (!email.trim().isEmpty()) {
                    try {
                        File tempFile = File.createTempFile("Invoice_" + bill.getBillId(), ".pdf");
                        String storedProtocol = billDAO.getAICareProtocol(bill.getBillId());
                        if (storedProtocol == null) storedProtocol = "";
                        else {
                            storedProtocol = storedProtocol
                                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                                .replaceAll("(?m)^#+\\s+", "<b>")
                                .replaceAll("(?m)^#+\\s+.*$", "$0</b>")
                                .replaceAll("`", "")
                                .trim();
                        }
                        reportService.generateInvoicePDF(items, bill.totalProperty().get(), bill.customerNameProperty().get(), tempFile.getAbsolutePath(), storedProtocol, bill.getBillId());
                        
                        org.example.MediManage.service.EmailService.sendInvoiceEmail(
                                email.trim(), bill.customerNameProperty().get(), storedProtocol,
                                tempFile.getAbsolutePath(), bill.getBillId(), bill.totalProperty().get()
                        ).thenAccept(res -> Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice sent via Email!")))
                         .exceptionally(ex -> {
                             Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to send Email: " + ex.getMessage()));
                             return null;
                         });
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to generate PDF: " + ex.getMessage());
                    }
                 }
            });
        });

        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10, btnEmail, btnWhatsApp, btnDownloadReceipt, btnDownload);
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
    // BUSINESS INTELLIGENCE CHARTS
    // ══════════════════════════════════════════════════════════════

    private void loadBICharts() {
        AppExecutors.runBackground(() -> {
            // Fetch all BI data off the FX thread
            List<Map.Entry<String, Double>> salesTrend = billDAO.getDailySalesTrend(30);
            Map<String, Double> paymentModes = billDAO.getSalesByPaymentMode();
            List<Map.Entry<String, Integer>> topProducts = billDAO.getTopSellingMedicines(10);
            Map<String, Double> expenseCategories = expenseDAO.getExpensesByCategory();

            Platform.runLater(() -> {
                // ── 1. Revenue Trend LineChart ──
                if (salesTrendChart != null) {
                    salesTrendChart.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Revenue");
                    for (Map.Entry<String, Double> entry : salesTrend) {
                        // Shorten date label to MM-DD
                        String label = entry.getKey().length() > 5 ? entry.getKey().substring(5) : entry.getKey();
                        series.getData().add(new XYChart.Data<>(label, entry.getValue()));
                    }
                    salesTrendChart.getData().add(series);
                }

                // ── 2. Payment Mode PieChart ──
                if (paymentModeChart != null) {
                    paymentModeChart.getData().clear();
                    for (Map.Entry<String, Double> entry : paymentModes.entrySet()) {
                        paymentModeChart.getData().add(
                                new PieChart.Data(entry.getKey() + " (₹" + String.format("%.0f", entry.getValue()) + ")", entry.getValue())
                        );
                    }
                }

                // ── 3. Top Products BarChart ──
                if (topProductsChart != null) {
                    topProductsChart.getData().clear();
                    XYChart.Series<String, Number> barSeries = new XYChart.Series<>();
                    barSeries.setName("Units Sold");
                    for (Map.Entry<String, Integer> entry : topProducts) {
                        // Truncate long medicine names
                        String name = entry.getKey().length() > 20 ? entry.getKey().substring(0, 17) + "..." : entry.getKey();
                        barSeries.getData().add(new XYChart.Data<>(name, entry.getValue()));
                    }
                    topProductsChart.getData().add(barSeries);
                }

                // ── 4. Expense Category PieChart ──
                if (expenseCategoryChart != null) {
                    expenseCategoryChart.getData().clear();
                    for (Map.Entry<String, Double> entry : expenseCategories.entrySet()) {
                        expenseCategoryChart.getData().add(
                                new PieChart.Data(entry.getKey() + " (₹" + String.format("%.0f", entry.getValue()) + ")", entry.getValue())
                        );
                    }
                }
            });
        });
    }

    // ══════════════════════════════════════════════════════════════
    // BUSINESS INTELLIGENCE (AI)
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleRestockReport() {
        var ctx = org.example.MediManage.util.AIResultDialog.showLoadingPopup(
                "Restock Forecasting", "", "Analyzing sales history (last 30 days) for trends...");
        inventoryAIService.generateRestockReport()
                .thenAccept(result -> ctx.setResult(result))
                .exceptionally(ex -> {
                    ctx.setResult("Error: " + ex.getMessage());
                    return null;
                });
    }

    @FXML
    private void handleExpiryReport() {
        var ctx = org.example.MediManage.util.AIResultDialog.showLoadingPopup(
                "Expiry Strategy", "", "Scanning inventory for expiring items...");
        inventoryAIService.generateExpiryReport()
                .thenAccept(result -> ctx.setResult(result))
                .exceptionally(ex -> {
                    ctx.setResult("Error: " + ex.getMessage());
                    return null;
                });
    }

    @FXML
    private void handleProfitAnalysis() {
        var ctx = org.example.MediManage.util.AIResultDialog.showLoadingPopup(
                "Profit Analyzer", "", "Analyzing profit margins across inventory...");
        inventoryAIService.generateProfitAnalysis()
                .thenAccept(result -> ctx.setResult(result))
                .exceptionally(ex -> {
                    ctx.setResult("Error: " + ex.getMessage());
                    return null;
                });
    }

    @FXML
    private void handleBusinessPulse() {
        var ctx = org.example.MediManage.util.AIResultDialog.showLoadingPopup(
                "Business Pulse", "", "Reviewing sales, inventory, and expiry signals...");
        inventoryAIService.askBusinessQuestion(
                        "Give me a pharmacy business pulse. Cover demand hotspots, low-stock risks, expiry exposure, "
                                + "cash tied in inventory, and finish with three actions for today.")
                .thenAccept(ctx::setResult)
                .exceptionally(ex -> {
                    ctx.setResult("Error: " + ex.getMessage());
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
