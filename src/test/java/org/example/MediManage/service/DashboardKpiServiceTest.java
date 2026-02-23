package org.example.MediManage.service;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.dao.PrescriptionDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.Medicine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardKpiServiceTest {

    @Test
    void getDashboardKpisClassifiesExpiryBucketsAcrossDefinedRanges() {
        DashboardKpiService service = new DashboardKpiService(
                new FakeBillDAO(),
                new FakeExpenseDAO(),
                new FakePrescriptionDAO(),
                new FakeSubscriptionDAO());

        LocalDate today = LocalDate.now();
        List<Medicine> inventory = List.of(
                medicine(1, today.minusDays(1), 5),
                medicine(2, today, 7),
                medicine(3, today.plusDays(30), 12),
                medicine(4, today.plusDays(31), 2),
                medicine(5, today.plusDays(60), 20),
                medicine(6, today.plusDays(61), 4),
                medicine(7, today.plusDays(90), 11),
                medicine(8, today.plusDays(91), 3),
                new Medicine(9, "Bad Date", "", "Co", "not-a-date", 8, 10.0));

        DashboardKpiService.DashboardKpis kpis = service.getDashboardKpis(inventory);

        assertEquals(1L, kpis.expiredMedicinesCount());
        assertEquals(2L, kpis.expiry0To30DaysCount());
        assertEquals(2L, kpis.expiry31To60DaysCount());
        assertEquals(2L, kpis.expiry61To90DaysCount());
        assertEquals(6L, kpis.lowStockCount());
        assertEquals(1200.0, kpis.dailySales(), 0.0001);
        assertEquals(300.0, kpis.monthlyExpenses(), 0.0001);
        assertEquals(4, kpis.pendingRxCount());
        assertEquals(5L, kpis.activeSubscribers());
        assertEquals(2L, kpis.renewalsDueSoon());
        assertEquals(75.0, kpis.dailySubscriptionSavings(), 0.0001);
        assertEquals(1L, kpis.pendingOverrideCount());
    }

    private static Medicine medicine(int id, LocalDate expiry, int stock) {
        return new Medicine(id, "Med-" + id, "", "Co", expiry.toString(), stock, 10.0);
    }

    private static class FakeBillDAO extends BillDAO {
        @Override
        public double getDailySales() {
            return 1200.0;
        }
    }

    private static class FakeExpenseDAO extends ExpenseDAO {
        @Override
        public double getMonthlyExpenses() {
            return 300.0;
        }
    }

    private static class FakePrescriptionDAO extends PrescriptionDAO {
        @Override
        public int countByStatus(String status) {
            return "PENDING".equals(status) ? 4 : 0;
        }
    }

    private static class FakeSubscriptionDAO extends SubscriptionDAO {
        @Override
        public SubscriptionDashboardSnapshot getDashboardSnapshot(int renewalWindowDays) {
            return new SubscriptionDashboardSnapshot(5L, 2L, 75.0, 1L);
        }
    }
}
