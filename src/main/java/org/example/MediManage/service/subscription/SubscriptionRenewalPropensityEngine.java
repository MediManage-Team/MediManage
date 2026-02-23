package org.example.MediManage.service.subscription;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SubscriptionRenewalPropensityEngine {
    public static final int DEFAULT_RENEWAL_WINDOW_DAYS = 21;
    public static final int DEFAULT_HISTORY_LOOKBACK_DAYS = 180;

    private static final int MIN_RENEWAL_WINDOW_DAYS = 1;
    private static final int MAX_RENEWAL_WINDOW_DAYS = 90;
    private static final DateTimeFormatter DB_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<RenewalPropensityScore> score(
            List<RenewalCandidate> candidates,
            int renewalWindowDays) {
        return score(candidates, renewalWindowDays, LocalDate.now());
    }

    List<RenewalPropensityScore> score(
            List<RenewalCandidate> candidates,
            int renewalWindowDays,
            LocalDate referenceDate) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int safeWindow = normalizeRenewalWindowDays(renewalWindowDays);
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        List<RenewalPropensityScore> rows = new ArrayList<>();
        for (RenewalCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            rows.add(scoreCandidate(candidate, safeWindow, safeReferenceDate));
        }
        rows.sort(Comparator
                .comparingDouble(RenewalPropensityScore::churnRiskScore).reversed()
                .thenComparingLong(RenewalPropensityScore::daysUntilRenewal)
                .thenComparing(RenewalPropensityScore::planName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(rows);
    }

    private RenewalPropensityScore scoreCandidate(
            RenewalCandidate candidate,
            int renewalWindowDays,
            LocalDate referenceDate) {
        LocalDate renewalDate = parseDate(candidate.enrollmentEndDate());
        long daysUntilRenewal = renewalDate == null ? renewalWindowDays : Math.max(0, ChronoUnit.DAYS.between(referenceDate, renewalDate));

        List<PurchaseEvent> purchases = candidate.purchaseEvents() == null ? List.of() : candidate.purchaseEvents();
        List<PurchaseEvent> refillEvents = candidate.refillEvents() == null ? List.of() : candidate.refillEvents();

        ActivitySignals activity = computeActivitySignals(purchases, refillEvents, referenceDate);

        double recencyRisk = clamp(activity.lastPurchaseDaysAgo() / 45.0, 0.0, 1.0);
        double lowFrequencyRisk = 1.0 - clamp(activity.purchaseCount30Days() / 4.0, 0.0, 1.0);
        double lowSpendRisk = 1.0 - clamp(activity.monthlySpendEstimate() / Math.max(50.0, candidate.planMonthlyCost() * 3.0), 0.0, 1.0);
        double lowRefillRegularityRisk = 1.0 - activity.refillRegularityScore();
        double renewalHistoryRisk = candidate.historicalRenewalCount() <= 0
                ? 0.85
                : clamp(0.9 / (candidate.historicalRenewalCount() + 1.0), 0.15, 0.65);
        double pricePressureRisk = clamp(
                candidate.planMonthlyCost() / Math.max(80.0, activity.monthlySpendEstimate() * 0.35),
                0.0,
                1.0);
        double urgencyMultiplier = clamp(
                0.70 + (0.30 * ((renewalWindowDays - Math.min(daysUntilRenewal, renewalWindowDays)) / (double) renewalWindowDays)),
                0.70,
                1.0);

        double baseRisk = (0.26 * recencyRisk)
                + (0.19 * lowFrequencyRisk)
                + (0.10 * lowSpendRisk)
                + (0.15 * lowRefillRegularityRisk)
                + (0.14 * renewalHistoryRisk)
                + (0.16 * pricePressureRisk);
        double churnProbability = clamp(baseRisk * urgencyMultiplier, 0.0, 1.0);
        double churnRiskScore = round2(churnProbability * 100.0);

        String riskBand = churnRiskScore >= 75.0
                ? "HIGH"
                : churnRiskScore >= 45.0 ? "MEDIUM" : "LOW";
        String recommendedAction = switch (riskBand) {
            case "HIGH" -> "Trigger proactive retention outreach and renewal call within 24 hours.";
            case "MEDIUM" -> "Send reminder + savings projection and follow up before renewal date.";
            default -> "Standard renewal reminder cadence is sufficient.";
        };

        String rationale = String.format(
                Locale.US,
                "Days to renewal: %d, purchases last 30d: %d, refill regularity: %.2f, renewal history: %d.",
                daysUntilRenewal,
                activity.purchaseCount30Days(),
                activity.refillRegularityScore(),
                candidate.historicalRenewalCount());

        return new RenewalPropensityScore(
                candidate.enrollmentId(),
                candidate.customerId(),
                candidate.planId(),
                candidate.planCode(),
                candidate.planName(),
                candidate.enrollmentEndDate(),
                daysUntilRenewal,
                churnRiskScore,
                round4(churnProbability),
                riskBand,
                activity.confidenceScore(),
                activity.monthlySpendEstimate(),
                activity.purchaseCount30Days(),
                activity.refillRegularityScore(),
                candidate.historicalRenewalCount(),
                recommendedAction,
                rationale);
    }

    private ActivitySignals computeActivitySignals(
            List<PurchaseEvent> purchases,
            List<PurchaseEvent> refillEvents,
            LocalDate referenceDate) {
        List<LocalDate> purchaseDates = new ArrayList<>();
        double spend30Days = 0.0;
        int purchaseCount30Days = 0;
        LocalDate latestPurchaseDate = null;
        for (PurchaseEvent event : purchases) {
            if (event == null) {
                continue;
            }
            LocalDateTime ts = parseTimestamp(event.eventDate());
            if (ts == null) {
                continue;
            }
            LocalDate eventDate = ts.toLocalDate();
            purchaseDates.add(eventDate);
            if (latestPurchaseDate == null || eventDate.isAfter(latestPurchaseDate)) {
                latestPurchaseDate = eventDate;
            }
            long daysAgo = ChronoUnit.DAYS.between(eventDate, referenceDate);
            if (daysAgo >= 0 && daysAgo <= 30) {
                purchaseCount30Days++;
                spend30Days += Math.max(0.0, event.amount());
            }
        }

        long lastPurchaseDaysAgo = latestPurchaseDate == null
                ? 365
                : Math.max(0, ChronoUnit.DAYS.between(latestPurchaseDate, referenceDate));
        double monthlySpendEstimate = round2(Math.max(0.0, spend30Days));
        if (monthlySpendEstimate <= 0.0 && !purchases.isEmpty()) {
            double total = purchases.stream().mapToDouble(row -> Math.max(0.0, row.amount())).sum();
            monthlySpendEstimate = round2(total / Math.max(1.0, purchases.size()) * 3.0);
        }

        double refillRegularityScore = computeRefillRegularity(refillEvents);
        double confidenceScore = round4(clamp(
                (0.55 * clamp(purchases.size() / 10.0, 0.0, 1.0))
                        + (0.25 * clamp(refillEvents.size() / 8.0, 0.0, 1.0))
                        + (0.20 * refillRegularityScore),
                0.0,
                1.0));

        return new ActivitySignals(
                monthlySpendEstimate,
                purchaseCount30Days,
                lastPurchaseDaysAgo,
                refillRegularityScore,
                confidenceScore);
    }

    private double computeRefillRegularity(List<PurchaseEvent> refillEvents) {
        if (refillEvents == null || refillEvents.size() < 2) {
            return 0.40;
        }
        List<LocalDate> refillDates = new ArrayList<>();
        for (PurchaseEvent event : refillEvents) {
            if (event == null) {
                continue;
            }
            LocalDateTime ts = parseTimestamp(event.eventDate());
            if (ts != null) {
                refillDates.add(ts.toLocalDate());
            }
        }
        if (refillDates.size() < 2) {
            return 0.40;
        }
        refillDates.sort(Comparator.naturalOrder());
        List<Long> intervals = new ArrayList<>();
        LocalDate previous = null;
        for (LocalDate current : refillDates) {
            if (previous != null) {
                long days = ChronoUnit.DAYS.between(previous, current);
                if (days > 0) {
                    intervals.add(days);
                }
            }
            previous = current;
        }
        if (intervals.size() < 2) {
            return 0.55;
        }
        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
        if (mean <= 0.0) {
            return 0.40;
        }
        double variance = intervals.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2.0))
                .average()
                .orElse(0.0);
        double sigma = Math.sqrt(Math.max(0.0, variance));
        double coefficientOfVariation = sigma / mean;
        return round4(clamp(1.0 - (Math.min(coefficientOfVariation, 1.5) / 1.5), 0.20, 1.0));
    }

    private int normalizeRenewalWindowDays(int renewalWindowDays) {
        if (renewalWindowDays <= 0) {
            return DEFAULT_RENEWAL_WINDOW_DAYS;
        }
        return Math.max(MIN_RENEWAL_WINDOW_DAYS, Math.min(MAX_RENEWAL_WINDOW_DAYS, renewalWindowDays));
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

    private LocalDate parseDate(String raw) {
        LocalDateTime ts = parseTimestamp(raw);
        return ts == null ? null : ts.toLocalDate();
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
            String eventDate,
            double amount) {
    }

    public record RenewalCandidate(
            int enrollmentId,
            int customerId,
            int planId,
            String planCode,
            String planName,
            String enrollmentStartDate,
            String enrollmentEndDate,
            double planMonthlyCost,
            int historicalRenewalCount,
            List<PurchaseEvent> purchaseEvents,
            List<PurchaseEvent> refillEvents) {
    }

    public record RenewalPropensityScore(
            int enrollmentId,
            int customerId,
            int planId,
            String planCode,
            String planName,
            String renewalDate,
            long daysUntilRenewal,
            double churnRiskScore,
            double churnProbability,
            String riskBand,
            double confidenceScore,
            double estimatedMonthlySpend,
            int purchasesLast30Days,
            double refillRegularityScore,
            int historicalRenewalCount,
            String recommendedAction,
            String rationale) {
    }

    private record ActivitySignals(
            double monthlySpendEstimate,
            int purchaseCount30Days,
            long lastPurchaseDaysAgo,
            double refillRegularityScore,
            double confidenceScore) {
    }
}
