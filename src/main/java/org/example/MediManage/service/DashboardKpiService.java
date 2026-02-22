package org.example.MediManage.service;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.dao.PrescriptionDAO;
import org.example.MediManage.model.Medicine;

import java.util.Collection;

public class DashboardKpiService {
    public record DashboardKpis(double dailySales, double monthlyExpenses, int pendingRxCount, long lowStockCount,
            double netProfit) {
    }

    private static final long KPI_TTL_MILLIS = 30_000;
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final DashboardKpiService INSTANCE = new DashboardKpiService();

    private final BillDAO billDAO;
    private final ExpenseDAO expenseDAO;
    private final PrescriptionDAO prescriptionDAO;

    private final Object lock = new Object();
    private double cachedDailySales;
    private long dailySalesCachedAt;
    private double cachedMonthlyExpenses;
    private long monthlyExpensesCachedAt;
    private int cachedPendingRxCount;
    private long pendingRxCachedAt;

    public DashboardKpiService() {
        this(new BillDAO(), new ExpenseDAO(), new PrescriptionDAO());
    }

    DashboardKpiService(BillDAO billDAO, ExpenseDAO expenseDAO, PrescriptionDAO prescriptionDAO) {
        this.billDAO = billDAO;
        this.expenseDAO = expenseDAO;
        this.prescriptionDAO = prescriptionDAO;
    }

    public static DashboardKpiService getInstance() {
        return INSTANCE;
    }

    public DashboardKpis getDashboardKpis(Collection<Medicine> inventorySnapshot) {
        double sales = getDailySales();
        double expenses = getMonthlyExpenses();
        int pendingRx = getPendingRxCount();
        long lowStockCount = countLowStock(inventorySnapshot);
        double netProfit = (sales * 0.2) - expenses;
        return new DashboardKpis(sales, expenses, pendingRx, lowStockCount, netProfit);
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

    private double getMonthlyExpenses() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!isExpired(monthlyExpensesCachedAt, now)) {
                return cachedMonthlyExpenses;
            }
            cachedMonthlyExpenses = expenseDAO.getMonthlyExpenses();
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

    private long countLowStock(Collection<Medicine> inventorySnapshot) {
        if (inventorySnapshot == null) {
            return 0;
        }
        return inventorySnapshot.stream().filter(m -> m.getStock() < LOW_STOCK_THRESHOLD).count();
    }

    private boolean isExpired(long cachedAt, long now) {
        return cachedAt == 0L || (now - cachedAt) > KPI_TTL_MILLIS;
    }

    private void invalidateSales() {
        synchronized (lock) {
            dailySalesCachedAt = 0L;
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
        }
    }
}
