package org.example.MediManage.service.subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SubscriptionDynamicOfferSuggestionEngine {

    public List<DynamicOfferSuggestion> suggest(
            List<OfferCandidate> candidates,
            String customerRiskBand,
            double customerRiskScore,
            int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int safeLimit = normalizeLimit(limit);
        double safeRiskScore = clamp(customerRiskScore, 0.0, 100.0);
        String safeRiskBand = customerRiskBand == null ? "LOW" : customerRiskBand.trim().toUpperCase(Locale.US);

        List<DynamicOfferSuggestion> rows = new ArrayList<>();
        for (OfferCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            rows.add(suggestForCandidate(candidate, safeRiskBand, safeRiskScore));
        }

        rows.sort(Comparator
                .comparingDouble(DynamicOfferSuggestion::expectedNetMonthlyBenefit).reversed()
                .thenComparingDouble(DynamicOfferSuggestion::offerDiscountPercent).reversed()
                .thenComparing(DynamicOfferSuggestion::planName, String.CASE_INSENSITIVE_ORDER));

        if (rows.size() <= safeLimit) {
            return List.copyOf(rows);
        }
        return List.copyOf(rows.subList(0, safeLimit));
    }

    private DynamicOfferSuggestion suggestForCandidate(
            OfferCandidate candidate,
            String customerRiskBand,
            double customerRiskScore) {
        double upliftPercent = computeRiskUplift(customerRiskBand, customerRiskScore);

        double rawOfferPercent = candidate.currentEffectiveDiscountPercent() + upliftPercent;
        double guardrailMaxByCap = clamp(candidate.planMaxDiscountPercent(), 0.0, 100.0);
        double guardrailMaxByMargin = clamp(100.0 - candidate.planMinimumMarginPercent(), 0.0, 100.0);
        double guardrailMax = Math.min(guardrailMaxByCap, guardrailMaxByMargin);
        double offerPercent = round2(clamp(rawOfferPercent, 0.0, guardrailMax));
        boolean capApplied = offerPercent + 0.0001 < round2(rawOfferPercent);

        double offerSavingsMultiplier = candidate.currentEffectiveDiscountPercent() <= 0.0
                ? 0.0
                : offerPercent / candidate.currentEffectiveDiscountPercent();
        double expectedMonthlySavings = round2(candidate.expectedMonthlySavings() * offerSavingsMultiplier);
        double incrementalSavings = round2(Math.max(0.0, expectedMonthlySavings - candidate.expectedMonthlySavings()));
        double expectedNetMonthlyBenefit = round2(expectedMonthlySavings - candidate.planMonthlyCost());

        String rationale = String.format(
                Locale.US,
                "Risk=%s %.1f/100, uplift %.2f%%, guardrail max %.2f%% (cap %.2f%%, margin floor %.2f%%).",
                customerRiskBand,
                customerRiskScore,
                upliftPercent,
                guardrailMax,
                guardrailMaxByCap,
                guardrailMaxByMargin);
        String guardrailSummary = capApplied
                ? "Offer clipped by guardrails."
                : "Offer within guardrails.";

        return new DynamicOfferSuggestion(
                candidate.planId(),
                candidate.planCode(),
                candidate.planName(),
                offerPercent,
                guardrailMaxByCap,
                guardrailMaxByMargin,
                capApplied,
                expectedMonthlySavings,
                incrementalSavings,
                expectedNetMonthlyBenefit,
                customerRiskBand,
                round2(customerRiskScore),
                rationale,
                guardrailSummary);
    }

    private double computeRiskUplift(String riskBand, double riskScore) {
        double bandBase = switch (riskBand) {
            case "HIGH" -> 5.0;
            case "MEDIUM" -> 2.5;
            default -> 1.0;
        };
        double scoreFactor = (riskScore / 100.0) * 2.0;
        return round2(bandBase + scoreFactor);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 5;
        }
        return Math.max(1, Math.min(100, limit));
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

    public record OfferCandidate(
            int planId,
            String planCode,
            String planName,
            double currentEffectiveDiscountPercent,
            double expectedMonthlySavings,
            double planMonthlyCost,
            double planMaxDiscountPercent,
            double planMinimumMarginPercent) {
    }

    public record DynamicOfferSuggestion(
            int planId,
            String planCode,
            String planName,
            double offerDiscountPercent,
            double guardrailMaxByPlanCapPercent,
            double guardrailMaxByMarginPercent,
            boolean guardrailCapApplied,
            double expectedMonthlySavings,
            double incrementalMonthlySavings,
            double expectedNetMonthlyBenefit,
            String customerRiskBand,
            double customerRiskScore,
            String rationale,
            String guardrailSummary) {
    }
}
