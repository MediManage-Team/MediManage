package org.example.MediManage.service;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.dao.PrescriptionDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.Medicine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

public class DashboardKpiService {
    public record DashboardKpis(
            double dailySales,
            double monthlyExpenses,
            int pendingRxCount,
            long lowStockCount,
            double netProfit,
            long activeSubscribers,
            long renewalsDueSoon,
            double dailySubscriptionSavings,
            long pendingOverrideCount,
            long expiredMedicinesCount,
            long expiry0To30DaysCount,
            long expiry31To60DaysCount,
            long expiry61To90DaysCount) {
    }

    private static final long KPI_TTL_MILLIS = 30_000;
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int RENEWAL_WINDOW_DAYS = 7;
    private static final DashboardKpiService INSTANCE = new DashboardKpiService();

    private final BillDAO billDAO;
    private final ExpenseDAO expenseDAO;
    private final PrescriptionDAO prescriptionDAO;
    private final SubscriptionDAO subscriptionDAO;

    private final Object lock = new Object();
    private double cachedDailySales;
    private long dailySalesCachedAt;
    private double cachedMonthlyExpenses;
    private long monthlyExpensesCachedAt;
    private int cachedPendingRxCount;
    private long pendingRxCachedAt;
    private SubscriptionDAO.SubscriptionDashboardSnapshot cachedSubscriptionSnapshot;
    private long subscriptionSnapshotCachedAt;

    public DashboardKpiService() {
        this(new BillDAO(), new ExpenseDAO(), new PrescriptionDAO(), new SubscriptionDAO());
    }

    DashboardKpiService(
            BillDAO billDAO,
            ExpenseDAO expenseDAO,
            PrescriptionDAO prescriptionDAO,
            SubscriptionDAO subscriptionDAO) {
        this.billDAO = billDAO;
        this.expenseDAO = expenseDAO;
        this.prescriptionDAO = prescriptionDAO;
        this.subscriptionDAO = subscriptionDAO;
        this.cachedSubscriptionSnapshot = new SubscriptionDAO.SubscriptionDashboardSnapshot(0L, 0L, 0.0, 0L);
    }

    public static DashboardKpiService getInstance() {
        return INSTANCE;
    }

    public DashboardKpis getDashboardKpis(Collection<Medicine> inventorySnapshot) {
        double sales = getDailySales();
        double expenses = getMonthlyExpenses();
        int pendingRx = getPendingRxCount();
        long lowStockCount = countLowStock(inventorySnapshot);
        ExpiryBuckets expiryBuckets = countExpiryBuckets(inventorySnapshot);
        SubscriptionDAO.SubscriptionDashboardSnapshot subscriptionSnapshot = getSubscriptionSnapshot();
        double netProfit = (sales * 0.2) - expenses;
        return new DashboardKpis(
                sales,
                expenses,
                pendingRx,
                lowStockCount,
                netProfit,
                subscriptionSnapshot.activeSubscribers(),
                subscriptionSnapshot.renewalsDueSoon(),
                subscriptionSnapshot.dailySubscriptionSavings(),
                subscriptionSnapshot.pendingOverrideCount(),
                expiryBuckets.expiredCount(),
                expiryBuckets.expiry0To30DaysCount(),
                expiryBuckets.expiry31To60DaysCount(),
                expiryBuckets.expiry61To90DaysCount());
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

    public static void invalidateSubscriptionMetrics() {
        INSTANCE.invalidateSubscriptions();
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

    private SubscriptionDAO.SubscriptionDashboardSnapshot getSubscriptionSnapshot() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!isExpired(subscriptionSnapshotCachedAt, now)) {
                return cachedSubscriptionSnapshot;
            }
            cachedSubscriptionSnapshot = subscriptionDAO.getDashboardSnapshot(RENEWAL_WINDOW_DAYS);
            subscriptionSnapshotCachedAt = now;
            return cachedSubscriptionSnapshot;
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

    private void invalidateSubscriptions() {
        synchronized (lock) {
            subscriptionSnapshotCachedAt = 0L;
        }
    }

    private void invalidateAll() {
        synchronized (lock) {
            dailySalesCachedAt = 0L;
            monthlyExpensesCachedAt = 0L;
            pendingRxCachedAt = 0L;
            subscriptionSnapshotCachedAt = 0L;
        }
    }

    private record ExpiryBuckets(
            long expiredCount,
            long expiry0To30DaysCount,
            long expiry31To60DaysCount,
            long expiry61To90DaysCount) {
    }
}
