package org.example.MediManage.service.ai;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.BillHistoryRecord;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.storage.StorageFactory;
import org.example.MediManage.storage.customer.CustomerStore;
import org.example.MediManage.util.AppExecutors;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AssistantReportService {
    public enum ReportType {
        INVENTORY,
        LOW_STOCK,
        EXPIRING,
        SALES,
        CUSTOMERS,
        TOP_SELLERS,
        PROFIT,
        DRUG_INTERACTIONS,
        REORDER,
        DAILY_SUMMARY
    }

    private final MedicineDAO medicineDAO;
    private final BillDAO billDAO;
    private final CustomerStore customerStore;

    public AssistantReportService() {
        this(new MedicineDAO(), new BillDAO(), StorageFactory.customerStore());
    }

    AssistantReportService(MedicineDAO medicineDAO, BillDAO billDAO, CustomerStore customerStore) {
        this.medicineDAO = medicineDAO;
        this.billDAO = billDAO;
        this.customerStore = customerStore;
    }

    public CompletableFuture<String> generate(ReportType reportType) {
        return CompletableFuture.supplyAsync(() -> switch (reportType) {
            case INVENTORY -> inventorySummary();
            case LOW_STOCK -> lowStockSummary();
            case EXPIRING -> expiringSummary();
            case SALES -> salesSummary();
            case CUSTOMERS -> customerBalanceSummary();
            case TOP_SELLERS -> topSellersSummary();
            case PROFIT -> profitSummary();
            case DRUG_INTERACTIONS -> drugInteractionSummary();
            case REORDER -> reorderSummary();
            case DAILY_SUMMARY -> dailySummary();
        }, AppExecutors.background());
    }

    private String inventorySummary() {
        List<Medicine> medicines = medicineDAO.getAllMedicines();
        StringBuilder sb = new StringBuilder("[Inventory Summary - " + medicines.size() + " active medicines]\n");
        medicines.stream().limit(20).forEach(m -> sb.append("- ")
                .append(m.getName())
                .append(" | Company: ").append(safe(m.getCompany()))
                .append(" | Price: ").append(currency(m.getPrice()))
                .append(" | Stock: ").append(m.getStock())
                .append(" | Expiry: ").append(safe(m.getExpiry()))
                .append('\n'));
        return sb.toString().trim();
    }

    private String lowStockSummary() {
        List<MedicineDAO.ReorderNeededRow> rows = medicineDAO.getReorderNeeded();
        StringBuilder sb = new StringBuilder("[Low Stock Medicines - " + rows.size() + " items]\n");
        rows.stream().limit(20).forEach(row -> sb.append("- ")
                .append(row.medicineName())
                .append(" | Company: ").append(safe(row.company()))
                .append(" | Stock: ").append(row.currentStock())
                .append(" | Reorder At: ").append(row.reorderThreshold())
                .append('\n'));
        if (rows.isEmpty()) {
            sb.append("- Status | Value: No medicines are below the reorder threshold right now\n");
        }
        return sb.toString().trim();
    }

    private String expiringSummary() {
        List<Medicine> medicines = medicineDAO.getExpiringMedicines(90);
        StringBuilder sb = new StringBuilder("[Expiring Medicines - Next 90 Days]\n");
        LocalDate today = LocalDate.now();
        medicines.stream().limit(20).forEach(m -> sb.append("- ")
                .append(m.getName())
                .append(" | Company: ").append(safe(m.getCompany()))
                .append(" | Expiry: ").append(safe(m.getExpiry()))
                .append(" | Days Left: ").append(daysLeft(today, m.getExpiry()))
                .append(" | Stock: ").append(m.getStock())
                .append('\n'));
        if (medicines.isEmpty()) {
            sb.append("- Status | Value: No medicines are expiring within the next 90 days\n");
        }
        return sb.toString().trim();
    }

    private String salesSummary() {
        LocalDate today = LocalDate.now();
        Map<String, Double> salesToday = billDAO.getSalesBetweenDates(today, today);
        Map<String, Integer> paymentDistribution = billDAO.getPaymentMethodDistribution(today, today);

        double totalRevenue = salesToday.values().stream().mapToDouble(Double::doubleValue).sum();
        int totalBills = paymentDistribution.values().stream().mapToInt(Integer::intValue).sum();
        double averageBill = totalBills == 0 ? 0.0 : totalRevenue / totalBills;

        StringBuilder sb = new StringBuilder("[Today's Sales Snapshot]\n");
        sb.append("- Total Revenue | Value: ").append(currency(totalRevenue)).append('\n');
        sb.append("- Total Bills | Value: ").append(totalBills).append('\n');
        sb.append("- Average Bill | Value: ").append(currency(averageBill)).append('\n');
        paymentDistribution.forEach((mode, count) -> sb.append("- Payment: ").append(mode)
                .append(" | Count: ").append(count).append('\n'));
        return sb.toString().trim();
    }

    private String customerBalanceSummary() {
        List<Customer> customers = new ArrayList<>(customerStore.getAllCustomers());
        customers.sort(Comparator.comparingDouble(Customer::getCurrentBalance).reversed());

        StringBuilder sb = new StringBuilder("[Customer Balance Summary]\n");
        customers.stream().filter(customer -> customer.getCurrentBalance() > 0.0).limit(20).forEach(customer -> sb.append("- ")
                .append(safe(customer.getName()))
                .append(" | Phone: ").append(safe(customer.getPhoneNumber()))
                .append(" | Balance: ").append(currency(customer.getCurrentBalance()))
                .append(" | Conditions: ").append(blankToDash(customer.getDiseases()))
                .append('\n'));

        if (sb.toString().trim().equals("[Customer Balance Summary]")) {
            sb.append("- Status | Value: No customers have an outstanding balance\n");
        }
        return sb.toString().trim();
    }

    private String topSellersSummary() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        Map<String, Integer> itemizedSales = billDAO.getItemizedSales(start, end);
        Map<String, Double> itemizedRevenue = billDAO.getItemizedRevenue(start, end);

        StringBuilder sb = new StringBuilder("[Top Selling Medicines - Last 30 Days]\n");
        itemizedSales.entrySet().stream().limit(15).forEach(entry -> sb.append("- ")
                .append(entry.getKey())
                .append(" | Units Sold: ").append(entry.getValue())
                .append(" | Revenue: ").append(currency(itemizedRevenue.getOrDefault(entry.getKey(), 0.0)))
                .append('\n'));
        if (itemizedSales.isEmpty()) {
            sb.append("- Status | Value: No medicine sales were recorded in the last 30 days\n");
        }
        return sb.toString().trim();
    }

    private String profitSummary() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        double totalRevenue = billDAO.getSalesBetweenDates(start, end).values().stream().mapToDouble(Double::doubleValue).sum();
        double totalProfit = billDAO.getProfitBetweenDates(start, end).values().stream().mapToDouble(Double::doubleValue).sum();
        int totalBills = billDAO.getPaymentMethodDistribution(start, end).values().stream().mapToInt(Integer::intValue).sum();
        double averageBill = totalBills == 0 ? 0.0 : totalRevenue / totalBills;
        double grossMargin = totalRevenue == 0 ? 0.0 : (totalProfit / totalRevenue) * 100.0;

        StringBuilder sb = new StringBuilder("[Profit Overview - Last 30 Days]\n");
        sb.append("- Revenue | Value: ").append(currency(totalRevenue)).append('\n');
        sb.append("- Net Profit | Value: ").append(currency(totalProfit)).append('\n');
        sb.append("- Gross Margin | Value: ").append(String.format("%.1f%%", grossMargin)).append('\n');
        sb.append("- Average Bill | Value: ").append(currency(averageBill)).append('\n');
        sb.append("- Bills Processed | Value: ").append(totalBills).append('\n');
        return sb.toString().trim();
    }

    private String drugInteractionSummary() {
        List<BillHistoryRecord> history = billDAO.getBillHistoryPage(0, 25);
        StringBuilder sb = new StringBuilder("[Recent Multi-Medicine Bills]\n");
        int rowCount = 0;
        for (BillHistoryRecord record : history) {
            List<BillItem> items = billDAO.getBillItemsExtended(record.getBillId());
            if (items.size() < 2) {
                continue;
            }
            rowCount++;
            String medicines = items.stream()
                    .map(BillItem::getName)
                    .distinct()
                    .limit(6)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("-");
            sb.append("- Bill #").append(record.getBillId())
                    .append(" | Customer: ").append(blankToDash(record.customerNameProperty().get()))
                    .append(" | Medicines: ").append(medicines)
                    .append('\n');
        }
        if (rowCount == 0) {
            sb.append("- Status | Value: No recent multi-medicine bills were found for interaction review\n");
        }
        return sb.toString().trim();
    }

    private String reorderSummary() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        Map<String, Integer> itemizedSales = billDAO.getItemizedSales(start, end);
        List<MedicineDAO.ReorderNeededRow> rows = medicineDAO.getReorderNeeded();

        StringBuilder sb = new StringBuilder("[Reorder Suggestions - Last 30 Days]\n");
        rows.stream().limit(20).forEach(row -> sb.append("- ")
                .append(row.medicineName())
                .append(" | Company: ").append(safe(row.company()))
                .append(" | Stock: ").append(row.currentStock())
                .append(" | Reorder At: ").append(row.reorderThreshold())
                .append(" | 30d Sales: ").append(itemizedSales.getOrDefault(row.medicineName(), 0))
                .append('\n'));
        if (rows.isEmpty()) {
            sb.append("- Status | Value: Nothing currently needs a reorder based on threshold settings\n");
        }
        return sb.toString().trim();
    }

    private String dailySummary() {
        LocalDate today = LocalDate.now();
        int lowStockCount = medicineDAO.getReorderNeeded().size();
        int expiringSoonCount = medicineDAO.getExpiringMedicines(60).size();
        double totalRevenue = billDAO.getSalesBetweenDates(today, today).values().stream().mapToDouble(Double::doubleValue).sum();
        int customersServed = billDAO.countTodaysUniqueCustomers();
        int totalBills = billDAO.countTodaysBills();

        StringBuilder sb = new StringBuilder("[Daily Pharmacy Summary]\n");
        sb.append("- Revenue Today | Value: ").append(currency(totalRevenue)).append('\n');
        sb.append("- Bills Today | Value: ").append(totalBills).append('\n');
        sb.append("- Customers Served | Value: ").append(customersServed).append('\n');
        sb.append("- Low Stock Alerts | Value: ").append(lowStockCount).append('\n');
        sb.append("- Expiring Within 60d | Value: ").append(expiringSoonCount).append('\n');
        return sb.toString().trim();
    }

    private String daysLeft(LocalDate today, String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return "-";
        }
        try {
            String safeExpiry = expiry.length() > 10 ? expiry.substring(0, 10) : expiry;
            long days = ChronoUnit.DAYS.between(today, LocalDate.parse(safeExpiry));
            return Long.toString(days);
        } catch (Exception ignored) {
            return "-";
        }
    }

    private String currency(double value) {
        return String.format("₹%.2f", value);
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
