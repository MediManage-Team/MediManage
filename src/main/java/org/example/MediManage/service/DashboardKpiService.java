package org.example.MediManage.service;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.PrescriptionDAO;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.util.ReportingWindowUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

public class DashboardKpiService {
    public record DashboardKpis(
            double dailySales,
            double monthlyExpenses,
            int pendingRxCount,
            long lowStockCount,
            double netProfit,
            int dailyBillCount,
            int dailyCustomerCount,
            long expiredMedicinesCount,
            long expiry0To30DaysCount,
            long expiry31To60DaysCount,
            long expiry61To90DaysCount,
            double avgProfitMargin) {
    }

    private static final long KPI_TTL_MILLIS = 30_000;
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final DashboardKpiService INSTANCE = new DashboardKpiService();

    private final BillDAO billDAO;
    private final ExpenseDAO expenseDAO;
    private final PrescriptionDAO prescriptionDAO;
    private final MedicineDAO medicineDAO;

    private final Object lock = new Object();
    private double cachedDailySales;
    private long dailySalesCachedAt;
    private double cachedMonthlyGrossProfit;
    private double cachedMonthlyExpenses;
    private long monthlyExpensesCachedAt;
    private int cachedPendingRxCount;
    private long pendingRxCachedAt;
    private int cachedDailyBillCount;
    private int cachedDailyCustomerCount;
    private long dailyBillsCachedAt;

    public DashboardKpiService() {
        this(new BillDAO(), new ExpenseDAO(), new PrescriptionDAO(), new MedicineDAO());
    }

    DashboardKpiService(BillDAO billDAO, ExpenseDAO expenseDAO, PrescriptionDAO prescriptionDAO) {
        this(billDAO, expenseDAO, prescriptionDAO, new MedicineDAO());
    }

    DashboardKpiService(BillDAO billDAO, ExpenseDAO expenseDAO, PrescriptionDAO prescriptionDAO,
            MedicineDAO medicineDAO) {
        this.billDAO = billDAO;
        this.expenseDAO = expenseDAO;
        this.prescriptionDAO = prescriptionDAO;
        this.medicineDAO = medicineDAO;
    }

    public static DashboardKpiService getInstance() {
        return INSTANCE;
    }

    public DashboardKpis getDashboardKpis(Collection<Medicine> inventorySnapshot) {
        double sales = getDailySales();
        double expenses = getMonthlyExpenses();
        double grossProfit = getMonthlyGrossProfit();
        int pendingRx = getPendingRxCount();
        long lowStockCount = countLowStock(inventorySnapshot);
        ExpiryBuckets expiryBuckets = countExpiryBuckets(inventorySnapshot);
        refreshDailyBillStats();
        double netProfit = grossProfit - expenses;
        double avgMargin = computeAvgProfitMargin(inventorySnapshot);
        return new DashboardKpis(
                sales,
                expenses,
                pendingRx,
                lowStockCount,
                netProfit,
                cachedDailyBillCount,
                cachedDailyCustomerCount,
                expiryBuckets.expiredCount(),
                expiryBuckets.expiry0To30DaysCount(),
                expiryBuckets.expiry31To60DaysCount(),
                expiryBuckets.expiry61To90DaysCount(),
                avgMargin);
    }

    public static void invalidateSalesMetrics() {
        INSTANCE.invalidateSales();
    }

    public static void invalidateExpenseMetrics() {
        INSTANCE.invalidateExpenses();
    }

    public static void invalidatePrescriptionMetrics() {
        INSTANCE.invalidatePendingRx();
    }

    public static void invalidateAllMetrics() {
        INSTANCE.invalidateAll();
    }

    private double getDailySales() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!isExpired(dailySalesCachedAt, now)) {
                return cachedDailySales;
            }
            cachedDailySales = billDAO.getDailySales();
            dailySalesCachedAt = now;
            return cachedDailySales;
        }
    }

    private double getMonthlyGrossProfit() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!isExpired(monthlyExpensesCachedAt, now)) {
                return cachedMonthlyGrossProfit;
            }
            cachedMonthlyGrossProfit = billDAO.getMonthlyGrossProfit();
            // Sharing cache TTL with monthly expenses for simplicity and sync
            return cachedMonthlyGrossProfit;
        }
    }

    private double getMonthlyExpenses() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!isExpired(monthlyExpensesCachedAt, now)) {
                return cachedMonthlyExpenses;
            }
            cachedMonthlyExpenses = expenseDAO.getMonthlyExpenses();
            cachedMonthlyGrossProfit = billDAO.getMonthlyGrossProfit(); // refresh parallel metric
            monthlyExpensesCachedAt = now;
            return cachedMonthlyExpenses;
        }
    }

    private int getPendingRxCount() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!isExpired(pendingRxCachedAt, now)) {
                return cachedPendingRxCount;
            }
            cachedPendingRxCount = prescriptionDAO.countByStatus("PENDING");
            pendingRxCachedAt = now;
            return cachedPendingRxCount;
        }
    }

    private void refreshDailyBillStats() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!isExpired(dailyBillsCachedAt, now)) {
                return;
            }
            cachedDailyBillCount = billDAO.countTodaysBills();
            cachedDailyCustomerCount = billDAO.countTodaysUniqueCustomers();
            dailyBillsCachedAt = now;
        }
    }

    private long countLowStock(Collection<Medicine> inventorySnapshot) {
        if (inventorySnapshot == null) {
            return 0;
        }
        return inventorySnapshot.stream().filter(m -> m.getStock() < LOW_STOCK_THRESHOLD).count();
    }

    private ExpiryBuckets countExpiryBuckets(Collection<Medicine> inventorySnapshot) {
        if (inventorySnapshot == null || inventorySnapshot.isEmpty()) {
            return new ExpiryBuckets(0L, 0L, 0L, 0L);
        }

        LocalDate today = LocalDate.now();
        long expired = 0L;
        long expiry0To30 = 0L;
        long expiry31To60 = 0L;
        long expiry61To90 = 0L;

        for (Medicine medicine : inventorySnapshot) {
            LocalDate expiryDate = parseExpiryDate(medicine == null ? null : medicine.getExpiry());
            if (expiryDate == null) {
                continue;
            }
            long daysToExpiry = ChronoUnit.DAYS.between(today, expiryDate);
            if (daysToExpiry < 0) {
                expired++;
            } else if (daysToExpiry <= 30) {
                expiry0To30++;
            } else if (daysToExpiry <= 60) {
                expiry31To60++;
            } else if (daysToExpiry <= 90) {
                expiry61To90++;
            }
        }

        return new ExpiryBuckets(expired, expiry0To30, expiry31To60, expiry61To90);
    }

    private LocalDate parseExpiryDate(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return null;
        }
        String normalized = expiry.trim();
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isExpired(long cachedAt, long now) {
        return cachedAt == 0L || (now - cachedAt) > KPI_TTL_MILLIS;
    }

    private void invalidateSales() {
        synchronized (lock) {
            dailySalesCachedAt = 0L;
            dailyBillsCachedAt = 0L;
        }
    }

    private void invalidateExpenses() {
        synchronized (lock) {
            monthlyExpensesCachedAt = 0L;
        }
    }

    private void invalidatePendingRx() {
        synchronized (lock) {
            pendingRxCachedAt = 0L;
        }
    }

    private void invalidateAll() {
        synchronized (lock) {
            dailySalesCachedAt = 0L;
            monthlyExpensesCachedAt = 0L;
            pendingRxCachedAt = 0L;
            dailyBillsCachedAt = 0L;
        }
    }

    private static double computeAvgProfitMargin(Collection<Medicine> medicines) {
        double sum = 0;
        int count = 0;
        for (Medicine m : medicines) {
            double margin = m.getProfitMarginPercent();
            if (margin > 0) {
                sum += margin;
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private record ExpiryBuckets(
            long expiredCount,
            long expiry0To30DaysCount,
            long expiry31To60DaysCount,
            long expiry61To90DaysCount) {
    }

    /**
     * Returns the weekly sales/margin summary for the current Monday–Sunday window.
     */
    public BillDAO.WeeklySalesMarginSummary getWeeklySalesSummary() {
        ReportingWindowUtils.WeeklyWindow window = ReportingWindowUtils.mondayToSundayWindow(LocalDate.now());
        return billDAO.getWeeklySalesMarginSummary(window.startDate(), window.endDate());
    }

    /**
     * Returns top N fast-moving medicines over the given lookback period.
     */
    public List<MedicineDAO.FastMovingInsightRow> getTopMovers(int lookbackDays, int limit) {
        return medicineDAO.getFastMovingInsights(lookbackDays, limit);
    }
}
