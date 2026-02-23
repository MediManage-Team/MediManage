package org.example.MediManage.util;

import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.Medicine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyAnomalyAlertEvaluatorTest {

    @Test
    void evaluateFlagsExpirySpikeWhenCurrentWeekRisesSharply() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 23); // Monday
        List<Medicine> inventory = List.of(
                med(1, "PrevWeekA", referenceDate.minusDays(7)),
                med(2, "PrevWeekB", referenceDate.minusDays(6)),
                med(3, "CurrWeekA", referenceDate.plusDays(1)),
                med(4, "CurrWeekB", referenceDate.plusDays(2)),
                med(5, "CurrWeekC", referenceDate.plusDays(3)),
                med(6, "CurrWeekD", referenceDate.plusDays(4)),
                med(7, "CurrWeekE", referenceDate.plusDays(5)));

        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts = WeeklyAnomalyAlertEvaluator.evaluate(
                inventory,
                List.of(),
                null,
                referenceDate);

        assertTrue(alerts.stream().anyMatch(alert -> "Expiry Spike".equals(alert.alertType())));
    }

    @Test
    void evaluateFlagsStockOutAndLeakageAlertsAtThresholds() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 23);
        List<MedicineDAO.OutOfStockInsightRow> outOfStockRows = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new MedicineDAO.OutOfStockInsightRow(
                        i + 1,
                        "Med-" + i,
                        "Co",
                        0,
                        null,
                        5,
                        0.0,
                        0.0,
                        0.0))
                .toList();

        SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySummary = new SubscriptionDAO.WeeklyAnalyticsSummaryRow(
                "2026-02-23",
                "2026-03-01",
                "Asia/Kolkata",
                100L,
                40L,
                10000.0,
                9200.0,
                800.0,
                8.0,
                50L,
                10L,
                2L,
                0L,
                0L,
                0L,
                "2026-03-01 10:00:00");

        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts = WeeklyAnomalyAlertEvaluator.evaluate(
                List.of(),
                outOfStockRows,
                weeklySummary,
                referenceDate);

        assertTrue(alerts.stream().anyMatch(alert -> "Stock-Out Spike".equals(alert.alertType())));
        assertTrue(alerts.stream().anyMatch(alert -> "Discount Leakage".equals(alert.alertType())));
    }

    @Test
    void evaluateReturnsNoAlertsWhenMetricsAreNormal() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 23);
        List<Medicine> inventory = List.of(
                med(1, "PrevWeekA", referenceDate.minusDays(7)),
                med(2, "CurrWeekA", referenceDate.plusDays(1)));

        SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySummary = new SubscriptionDAO.WeeklyAnalyticsSummaryRow(
                "2026-02-23",
                "2026-03-01",
                "Asia/Kolkata",
                20L,
                5L,
                5000.0,
                4900.0,
                100.0,
                2.0,
                10L,
                2L,
                0L,
                0L,
                0L,
                0L,
                "2026-03-01 10:00:00");

        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts = WeeklyAnomalyAlertEvaluator.evaluate(
                inventory,
                List.of(),
                weeklySummary,
                referenceDate);

        assertFalse(alerts.stream().anyMatch(alert -> "Stock-Out Spike".equals(alert.alertType())));
        assertFalse(alerts.stream().anyMatch(alert -> "Discount Leakage".equals(alert.alertType())));
    }

    @Test
    void evaluateFlagsAiLeakageSpikeAgainstHistoricalBaseline() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 23);
        SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySummary = new SubscriptionDAO.WeeklyAnalyticsSummaryRow(
                "2026-02-23",
                "2026-03-01",
                "Asia/Kolkata",
                120L,
                60L,
                12000.0,
                11184.0,
                816.0,
                6.8,
                70L,
                12L,
                1L,
                0L,
                0L,
                0L,
                "2026-03-01 10:00:00");
        List<SubscriptionDAO.WeeklyLeakageHistoryRow> history = List.of(
                history("2026-01-20", 2.2),
                history("2026-01-27", 2.4),
                history("2026-02-03", 2.1),
                history("2026-02-10", 2.5),
                history("2026-02-17", 2.3));

        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts = WeeklyAnomalyAlertEvaluator.evaluate(
                List.of(),
                List.of(),
                weeklySummary,
                history,
                referenceDate);

        assertTrue(alerts.stream().anyMatch(alert -> "AI Leakage Spike".equals(alert.alertType())));
        assertFalse(alerts.stream().anyMatch(alert -> "Discount Leakage".equals(alert.alertType())));
    }

    @Test
    void evaluateDoesNotFlagAiLeakageSpikeWhenBaselineHistoryTooShort() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 23);
        SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySummary = new SubscriptionDAO.WeeklyAnalyticsSummaryRow(
                "2026-02-23",
                "2026-03-01",
                "Asia/Kolkata",
                80L,
                40L,
                8000.0,
                7480.0,
                520.0,
                6.5,
                50L,
                8L,
                1L,
                0L,
                0L,
                0L,
                "2026-03-01 10:00:00");
        List<SubscriptionDAO.WeeklyLeakageHistoryRow> shortHistory = List.of(
                history("2026-02-03", 2.0),
                history("2026-02-10", 2.3),
                history("2026-02-17", 2.1));

        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts = WeeklyAnomalyAlertEvaluator.evaluate(
                List.of(),
                List.of(),
                weeklySummary,
                shortHistory,
                referenceDate);

        assertFalse(alerts.stream().anyMatch(alert -> "AI Leakage Spike".equals(alert.alertType())));
    }

    @Test
    void evaluateDoesNotFlagAiLeakageSpikeWhenCurrentLeakageMatchesBaselineBand() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 23);
        SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySummary = new SubscriptionDAO.WeeklyAnalyticsSummaryRow(
                "2026-02-23",
                "2026-03-01",
                "Asia/Kolkata",
                100L,
                55L,
                10000.0,
                9480.0,
                520.0,
                5.2,
                60L,
                9L,
                1L,
                0L,
                0L,
                0L,
                "2026-03-01 10:00:00");
        List<SubscriptionDAO.WeeklyLeakageHistoryRow> history = List.of(
                history("2026-01-20", 4.9),
                history("2026-01-27", 5.1),
                history("2026-02-03", 5.0),
                history("2026-02-10", 5.2),
                history("2026-02-17", 5.1));

        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts = WeeklyAnomalyAlertEvaluator.evaluate(
                List.of(),
                List.of(),
                weeklySummary,
                history,
                referenceDate);

        assertFalse(alerts.stream().anyMatch(alert -> "AI Leakage Spike".equals(alert.alertType())));
    }

    private static Medicine med(int id, String name, LocalDate expiry) {
        return new Medicine(id, name, "", "Co", expiry.toString(), 10, 10.0);
    }

    private static SubscriptionDAO.WeeklyLeakageHistoryRow history(String weekStartDate, double leakagePercent) {
        LocalDate weekStart = LocalDate.parse(weekStartDate);
        return new SubscriptionDAO.WeeklyLeakageHistoryRow(
                weekStartDate,
                weekStart.plusDays(6).toString(),
                "Asia/Kolkata",
                leakagePercent,
                100.0,
                1000.0,
                weekStart.plusDays(6) + " 10:00:00");
    }
}
