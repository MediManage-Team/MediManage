package org.example.MediManage.util;

import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.Medicine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class WeeklyAnomalyAlertEvaluator {
    public static final double EXPIRY_SPIKE_THRESHOLD_PERCENT = 50.0;
    public static final int EXPIRY_SPIKE_MIN_ALERT_COUNT = 5;
    public static final int STOCK_OUT_SPIKE_THRESHOLD = 10;
    public static final double DISCOUNT_LEAKAGE_THRESHOLD_PERCENT = 8.0;
    public static final int AI_LEAKAGE_BASELINE_MIN_WEEKS = 4;
    public static final double AI_LEAKAGE_SIGMA_SPIKE_THRESHOLD = 2.0;
    public static final double AI_LEAKAGE_RATIO_SPIKE_THRESHOLD = 1.5;
    public static final double AI_LEAKAGE_MIN_DELTA_PERCENTAGE_POINTS = 1.5;

    private WeeklyAnomalyAlertEvaluator() {
    }

    public static List<AnomalyAlert> evaluate(
            List<Medicine> inventory,
            List<MedicineDAO.OutOfStockInsightRow> outOfStockRows,
            SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySubscriptionSummary,
            LocalDate referenceDate) {
        return evaluate(inventory, outOfStockRows, weeklySubscriptionSummary, List.of(), referenceDate);
    }

    public static List<AnomalyAlert> evaluate(
            List<Medicine> inventory,
            List<MedicineDAO.OutOfStockInsightRow> outOfStockRows,
            SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySubscriptionSummary,
            List<SubscriptionDAO.WeeklyLeakageHistoryRow> weeklyLeakageHistory,
            LocalDate referenceDate) {
        List<AnomalyAlert> alerts = new ArrayList<>();
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        ReportingWindowUtils.WeeklyWindow currentWeek = ReportingWindowUtils.mondayToSundayWindow(safeReferenceDate);
        ReportingWindowUtils.WeeklyWindow previousWeek = new ReportingWindowUtils.WeeklyWindow(
                currentWeek.startDate().minusDays(7),
                currentWeek.endDate().minusDays(7));

        int currentWeekExpiryCount = countExpiryInWindow(inventory, currentWeek.startDate(), currentWeek.endDate());
        int previousWeekExpiryCount = countExpiryInWindow(inventory, previousWeek.startDate(), previousWeek.endDate());

        double expirySpikePercent = previousWeekExpiryCount <= 0
                ? (currentWeekExpiryCount > 0 ? 100.0 : 0.0)
                : ((currentWeekExpiryCount - previousWeekExpiryCount) * 100.0) / previousWeekExpiryCount;

        if ((previousWeekExpiryCount > 0 && expirySpikePercent >= EXPIRY_SPIKE_THRESHOLD_PERCENT
                && currentWeekExpiryCount > previousWeekExpiryCount)
                || (previousWeekExpiryCount == 0 && currentWeekExpiryCount >= EXPIRY_SPIKE_MIN_ALERT_COUNT)) {
            String severity = currentWeekExpiryCount >= (EXPIRY_SPIKE_MIN_ALERT_COUNT * 2) ? "HIGH" : "MEDIUM";
            alerts.add(new AnomalyAlert(
                    "Expiry Spike",
                    severity,
                    String.format("Current week expiries: %d | Previous week: %d", currentWeekExpiryCount, previousWeekExpiryCount),
                    String.format("Spike >= %.1f%% or current >= %d", EXPIRY_SPIKE_THRESHOLD_PERCENT, EXPIRY_SPIKE_MIN_ALERT_COUNT),
                    String.format("Expiry count increased by %.1f%% week-over-week.", expirySpikePercent)));
        }

        int outOfStockCount = outOfStockRows == null ? 0 : outOfStockRows.size();
        if (outOfStockCount >= STOCK_OUT_SPIKE_THRESHOLD) {
            String severity = outOfStockCount >= (STOCK_OUT_SPIKE_THRESHOLD * 2) ? "HIGH" : "MEDIUM";
            alerts.add(new AnomalyAlert(
                    "Stock-Out Spike",
                    severity,
                    "Out-of-stock SKUs: " + outOfStockCount,
                    "SKUs >= " + STOCK_OUT_SPIKE_THRESHOLD,
                    "Out-of-stock SKU count crossed the weekly monitoring threshold."));
        }

        double leakagePercent = weeklySubscriptionSummary == null ? 0.0 : weeklySubscriptionSummary.leakagePercent();
        if (leakagePercent >= DISCOUNT_LEAKAGE_THRESHOLD_PERCENT) {
            String severity = leakagePercent >= (DISCOUNT_LEAKAGE_THRESHOLD_PERCENT * 2.0) ? "HIGH" : "MEDIUM";
            alerts.add(new AnomalyAlert(
                    "Discount Leakage",
                    severity,
                    String.format("Leakage: %.2f%%", leakagePercent),
                    String.format("Leakage >= %.2f%%", DISCOUNT_LEAKAGE_THRESHOLD_PERCENT),
                    "Subscription-linked discount leakage is above acceptable threshold."));
        }

        evaluateAiLeakageSpike(weeklySubscriptionSummary, weeklyLeakageHistory)
                .ifPresent(alerts::add);

        return alerts;
    }

    private static java.util.Optional<AnomalyAlert> evaluateAiLeakageSpike(
            SubscriptionDAO.WeeklyAnalyticsSummaryRow weeklySubscriptionSummary,
            List<SubscriptionDAO.WeeklyLeakageHistoryRow> weeklyLeakageHistory) {
        if (weeklySubscriptionSummary == null) {
            return java.util.Optional.empty();
        }
        List<Double> baselineLeakagePercents = (weeklyLeakageHistory == null ? List.<SubscriptionDAO.WeeklyLeakageHistoryRow>of() : weeklyLeakageHistory)
                .stream()
                .map(SubscriptionDAO.WeeklyLeakageHistoryRow::leakagePercent)
                .filter(Double::isFinite)
                .filter(value -> value >= 0.0)
                .toList();

        if (baselineLeakagePercents.size() < AI_LEAKAGE_BASELINE_MIN_WEEKS) {
            return java.util.Optional.empty();
        }

        double currentLeakagePercent = weeklySubscriptionSummary.leakagePercent();
        double baselineAverage = baselineLeakagePercents.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double baselineVariance = baselineLeakagePercents.stream()
                .mapToDouble(value -> Math.pow(value - baselineAverage, 2.0))
                .average()
                .orElse(0.0);
        double baselineSigma = Math.sqrt(Math.max(0.0, baselineVariance));
        double deltaPercentagePoints = currentLeakagePercent - baselineAverage;
        double sigmaScore = baselineSigma <= 0.0
                ? (deltaPercentagePoints > 0.0 ? Double.POSITIVE_INFINITY : 0.0)
                : deltaPercentagePoints / baselineSigma;
        double ratioMultiplier = baselineAverage <= 0.0
                ? (currentLeakagePercent > 0.0 ? Double.POSITIVE_INFINITY : 1.0)
                : currentLeakagePercent / baselineAverage;

        boolean isSpike = deltaPercentagePoints >= AI_LEAKAGE_MIN_DELTA_PERCENTAGE_POINTS
                && (sigmaScore >= AI_LEAKAGE_SIGMA_SPIKE_THRESHOLD
                        || ratioMultiplier >= AI_LEAKAGE_RATIO_SPIKE_THRESHOLD);
        if (!isSpike) {
            return java.util.Optional.empty();
        }

        String severity = (sigmaScore >= 3.0 || ratioMultiplier >= 2.0 || deltaPercentagePoints >= 4.0)
                ? "HIGH"
                : "MEDIUM";
        return java.util.Optional.of(new AnomalyAlert(
                "AI Leakage Spike",
                severity,
                String.format(
                        "Current: %.2f%% | Baseline avg: %.2f%% | Delta: +%.2fpp | Sigma: %.2f",
                        currentLeakagePercent,
                        baselineAverage,
                        deltaPercentagePoints,
                        sigmaScore),
                String.format(
                        "AI baseline >= %d weeks and Delta >= %.2fpp and (Sigma >= %.2f or ratio >= %.2fx)",
                        AI_LEAKAGE_BASELINE_MIN_WEEKS,
                        AI_LEAKAGE_MIN_DELTA_PERCENTAGE_POINTS,
                        AI_LEAKAGE_SIGMA_SPIKE_THRESHOLD,
                        AI_LEAKAGE_RATIO_SPIKE_THRESHOLD),
                "AI baseline anomaly detection found sudden subscription discount leakage acceleration."));
    }

    private static int countExpiryInWindow(List<Medicine> inventory, LocalDate startDate, LocalDate endDate) {
        if (inventory == null || inventory.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Medicine medicine : inventory) {
            if (medicine == null) {
                continue;
            }
            LocalDate expiryDate = parseLocalDate(medicine.getExpiry());
            if (expiryDate == null) {
                continue;
            }
            if (!expiryDate.isBefore(startDate) && !expiryDate.isAfter(endDate)) {
                count++;
            }
        }
        return count;
    }

    private static LocalDate parseLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record AnomalyAlert(
            String alertType,
            String severity,
            String metricValue,
            String thresholdRule,
            String message) {
    }
}
