package org.example.MediManage.service.subscription;

import org.example.MediManage.model.SubscriptionPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubscriptionPlanRecommendationEngine {
    public static final int DEFAULT_LOOKBACK_DAYS = 180;

    private static final int MIN_LOOKBACK_DAYS = 30;
    private static final int MAX_LOOKBACK_DAYS = 365;
    private static final DateTimeFormatter DB_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RecommendationResult recommend(
            List<SubscriptionPlan> candidatePlans,
            List<PurchaseEvent> purchaseEvents,
            List<RefillEvent> refillEvents,
            int lookbackDays) {
        return recommend(candidatePlans, purchaseEvents, refillEvents, lookbackDays, LocalDate.now());
    }

    RecommendationResult recommend(
            List<SubscriptionPlan> candidatePlans,
            List<PurchaseEvent> purchaseEvents,
            List<RefillEvent> refillEvents,
            int lookbackDays,
            LocalDate referenceDate) {
        int safeLookbackDays = normalizeLookbackDays(lookbackDays);
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        List<SubscriptionPlan> plans = safePlans(candidatePlans);
        CustomerBehaviorSnapshot behavior = computeBehavior(purchaseEvents, refillEvents, safeLookbackDays, safeReferenceDate);

        if (plans.isEmpty()) {
            return new RecommendationResult(
                    behavior,
                    List.of(),
                    "No active or draft subscription plans are available for recommendation.");
        }

        if (behavior.billCount() == 0) {
            return new RecommendationResult(
                    behavior,
                    List.of(),
                    "No customer purchase history found in the last " + safeLookbackDays + " days.");
        }

        List<PlanRecommendation> recommendations = new ArrayList<>();
        for (SubscriptionPlan plan : plans) {
            recommendations.add(scorePlan(plan, behavior));
        }

        recommendations.sort(Comparator
                .comparingDouble(PlanRecommendation::recommendationScore).reversed()
                .thenComparingDouble(PlanRecommendation::expectedNetMonthlyBenefit).reversed()
                .thenComparingDouble(PlanRecommendation::expectedMonthlySavings).reversed()
                .thenComparing(PlanRecommendation::planName, String.CASE_INSENSITIVE_ORDER));

        String statusMessage = behavior.billCount() < 2
                ? "Limited purchase history; recommendation confidence is low."
                : "Recommendation generated from purchase history, refill behavior, and expected savings.";
        return new RecommendationResult(behavior, List.copyOf(recommendations), statusMessage);
    }

    private List<SubscriptionPlan> safePlans(List<SubscriptionPlan> candidatePlans) {
        if (candidatePlans == null || candidatePlans.isEmpty()) {
            return List.of();
        }
        List<SubscriptionPlan> rows = new ArrayList<>();
        for (SubscriptionPlan plan : candidatePlans) {
            if (plan == null) {
                continue;
            }
            if (plan.durationDays() <= 0) {
                continue;
            }
            rows.add(plan);
        }
        return rows;
    }

    private CustomerBehaviorSnapshot computeBehavior(
            List<PurchaseEvent> purchaseEvents,
            List<RefillEvent> refillEvents,
            int lookbackDays,
            LocalDate referenceDate) {
        List<PurchasePoint> purchases = new ArrayList<>();
        if (purchaseEvents != null) {
            for (PurchaseEvent event : purchaseEvents) {
                if (event == null) {
                    continue;
                }
                LocalDateTime billDate = parseTimestamp(event.billDate());
                if (billDate == null) {
                    continue;
                }
                purchases.add(new PurchasePoint(billDate, Math.max(0.0, event.billedAmount())));
            }
        }
        purchases.sort(Comparator.comparing(PurchasePoint::billDate));

        if (purchases.isEmpty()) {
            return new CustomerBehaviorSnapshot(
                    0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    lookbackDays,
                    lookbackDays);
        }

        double totalSpend = purchases.stream().mapToDouble(PurchasePoint::billedAmount).sum();
        long billCount = purchases.size();
        LocalDate firstDate = purchases.get(0).billDate().toLocalDate();
        int observationDays = (int) Math.max(1, ChronoUnit.DAYS.between(firstDate, referenceDate) + 1);
        observationDays = Math.max(MIN_LOOKBACK_DAYS, observationDays);
        observationDays = Math.min(observationDays, lookbackDays);

        double monthlySpend = round2(totalSpend * 30.0 / observationDays);
        double monthlyBills = round4((billCount * 30.0) / observationDays);

        RefillComputation refill = computeRefillSignal(refillEvents, monthlyBills);
        double historySignal = clamp(billCount / 10.0, 0.0, 1.0);
        double refillSignal = clamp(refill.intervalCount() / 8.0, 0.0, 1.0);
        double confidenceScore = round4(clamp(
                (0.55 * historySignal) + (0.30 * refill.refillRegularityScore()) + (0.15 * refillSignal),
                0.0,
                1.0));

        return new CustomerBehaviorSnapshot(
                billCount,
                refill.refillEventCount(),
                refill.intervalCount(),
                monthlySpend,
                monthlyBills,
                refill.estimatedRefillsPerMonth(),
                refill.refillRegularityScore(),
                confidenceScore,
                observationDays,
                lookbackDays);
    }

    private RefillComputation computeRefillSignal(List<RefillEvent> refillEvents, double monthlyBills) {
        if (refillEvents == null || refillEvents.isEmpty()) {
            double baselineRegularity = round4(clamp(0.30 + (monthlyBills / 12.0), 0.30, 0.75));
            double baselineRefills = round4(Math.max(0.0, monthlyBills));
            return new RefillComputation(0, 0, baselineRefills, baselineRegularity);
        }

        Map<Integer, List<LocalDate>> purchaseDaysByMedicine = new HashMap<>();
        int refillEventCount = 0;
        for (RefillEvent event : refillEvents) {
            if (event == null || event.medicineId() <= 0 || event.quantity() <= 0) {
                continue;
            }
            LocalDateTime billDate = parseTimestamp(event.billDate());
            if (billDate == null) {
                continue;
            }
            refillEventCount++;
            purchaseDaysByMedicine.computeIfAbsent(event.medicineId(), ignored -> new ArrayList<>())
                    .add(billDate.toLocalDate());
        }

        List<Long> intervals = new ArrayList<>();
        for (List<LocalDate> dates : purchaseDaysByMedicine.values()) {
            dates.sort(Comparator.naturalOrder());
            LocalDate previous = null;
            for (LocalDate current : dates) {
                if (previous != null) {
                    long days = ChronoUnit.DAYS.between(previous, current);
                    if (days > 0) {
                        intervals.add(days);
                    }
                }
                previous = current;
            }
        }

        double estimatedRefillsPerMonth;
        if (intervals.isEmpty()) {
            estimatedRefillsPerMonth = round4(Math.max(0.0, monthlyBills));
        } else {
            double meanInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
            estimatedRefillsPerMonth = meanInterval <= 0.0
                    ? round4(Math.max(0.0, monthlyBills))
                    : round4(clamp(30.0 / meanInterval, 0.0, 12.0));
        }

        double refillRegularityScore;
        if (intervals.size() < 2) {
            refillRegularityScore = round4(clamp(0.30 + (monthlyBills / 12.0), 0.30, 0.75));
        } else {
            double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
            if (mean <= 0.0) {
                refillRegularityScore = 0.30;
            } else {
                double variance = intervals.stream()
                        .mapToDouble(value -> Math.pow(value - mean, 2.0))
                        .average()
                        .orElse(0.0);
                double sigma = Math.sqrt(Math.max(0.0, variance));
                double coefficientOfVariation = sigma / mean;
                refillRegularityScore = round4(clamp(
                        1.0 - (Math.min(coefficientOfVariation, 1.5) / 1.5),
                        0.20,
                        1.0));
            }
        }

        return new RefillComputation(
                refillEventCount,
                intervals.size(),
                estimatedRefillsPerMonth,
                refillRegularityScore);
    }

    private PlanRecommendation scorePlan(SubscriptionPlan plan, CustomerBehaviorSnapshot behavior) {
        double monthlySpend = behavior.estimatedMonthlySpend();
        double normalizedDefaultDiscount = clamp(plan.defaultDiscountPercent(), 0.0, 100.0);
        double normalizedMaxDiscount = clamp(plan.maxDiscountPercent(), 0.0, 100.0);
        double effectiveDiscountPercent = round4(Math.min(normalizedDefaultDiscount, normalizedMaxDiscount));

        double applicabilityRatio = clamp(
                0.40 + (0.35 * behavior.refillRegularityScore()) + (0.25 * behavior.confidenceScore()),
                0.35,
                0.95);
        if (behavior.billCount() < 2) {
            applicabilityRatio = round4(applicabilityRatio * 0.65);
        }

        double expectedMonthlySavings = round2(monthlySpend * (effectiveDiscountPercent / 100.0) * applicabilityRatio);
        double monthlyPlanCost = round2(Math.max(0.0, plan.price()) * (30.0 / Math.max(1, plan.durationDays())));
        double expectedNetMonthlyBenefit = round2(expectedMonthlySavings - monthlyPlanCost);
        double expectedAnnualNetBenefit = round2(expectedNetMonthlyBenefit * 12.0);

        double netBenefitDenominator = Math.max(50.0, monthlySpend * 0.20);
        double netBenefitScore = clamp((expectedNetMonthlyBenefit / netBenefitDenominator + 1.0) / 2.0, 0.0, 1.0);
        double savingsScore = clamp(
                expectedMonthlySavings / Math.max(1.0, monthlySpend * 0.20),
                0.0,
                1.0);
        double refillFitScore = clamp(behavior.estimatedRefillsPerMonth() / 4.0, 0.0, 1.0);
        double recommendationScore = round2(100.0 * (
                (0.50 * netBenefitScore)
                        + (0.25 * savingsScore)
                        + (0.15 * behavior.refillRegularityScore())
                        + (0.10 * refillFitScore)));

        String rationale;
        if (expectedNetMonthlyBenefit >= 0.0) {
            rationale = String.format(
                    Locale.US,
                    "Expected savings %.2f/month vs plan cost %.2f/month (%s%.2f net).",
                    expectedMonthlySavings,
                    monthlyPlanCost,
                    "+",
                    expectedNetMonthlyBenefit);
        } else {
            rationale = String.format(
                    Locale.US,
                    "Expected savings %.2f/month vs plan cost %.2f/month (%.2f net).",
                    expectedMonthlySavings,
                    monthlyPlanCost,
                    expectedNetMonthlyBenefit);
        }

        return new PlanRecommendation(
                plan.planId(),
                plan.planCode(),
                plan.planName(),
                recommendationScore,
                expectedMonthlySavings,
                monthlyPlanCost,
                expectedNetMonthlyBenefit,
                expectedAnnualNetBenefit,
                effectiveDiscountPercent,
                rationale);
    }

    private int normalizeLookbackDays(int lookbackDays) {
        if (lookbackDays <= 0) {
            return DEFAULT_LOOKBACK_DAYS;
        }
        return Math.max(MIN_LOOKBACK_DAYS, Math.min(MAX_LOOKBACK_DAYS, lookbackDays));
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.length() > 19) {
            normalized = normalized.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(normalized, DB_TIMESTAMP);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record PurchaseEvent(
            String billDate,
            double billedAmount) {
    }

    public record RefillEvent(
            int medicineId,
            String billDate,
            int quantity,
            double lineAmount) {
    }

    public record CustomerBehaviorSnapshot(
            long billCount,
            int refillEventCount,
            int refillIntervalCount,
            double estimatedMonthlySpend,
            double estimatedMonthlyBills,
            double estimatedRefillsPerMonth,
            double refillRegularityScore,
            double confidenceScore,
            int observationDays,
            int lookbackDays) {
    }

    public record PlanRecommendation(
            int planId,
            String planCode,
            String planName,
            double recommendationScore,
            double expectedMonthlySavings,
            double expectedMonthlyPlanCost,
            double expectedNetMonthlyBenefit,
            double expectedAnnualNetBenefit,
            double effectiveDiscountPercent,
            String rationale) {
    }

    public record RecommendationResult(
            CustomerBehaviorSnapshot behavior,
            List<PlanRecommendation> recommendations,
            String statusMessage) {
    }

    private record PurchasePoint(
            LocalDateTime billDate,
            double billedAmount) {
    }

    private record RefillComputation(
            int refillEventCount,
            int intervalCount,
            double estimatedRefillsPerMonth,
            double refillRegularityScore) {
    }
}
