package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.SubscriptionDiscountOverride;

import java.util.Locale;

public class SubscriptionOverrideRiskScoringEngine {

    public OverrideRiskAssessment score(
            SubscriptionDiscountOverride override,
            SubscriptionDAO.OverrideAbuseSignalRow requesterSignal,
            SubscriptionDAO.EnrollmentAbuseSignalRow customerSignal,
            int lookbackDays) {
        if (override == null) {
            throw new IllegalArgumentException("Override is required for risk scoring.");
        }

        int score = 0;
        score += scoreRequestedDiscount(override.requestedDiscountPercent());
        score += scoreRequesterPattern(requesterSignal);
        score += scoreCustomerPattern(customerSignal);
        score += scoreReasonQuality(override.reason());
        score = Math.max(0, Math.min(100, score));

        String riskBand = score >= 75 ? "HIGH" : score >= 45 ? "MEDIUM" : "LOW";
        boolean escalationRecommended = "HIGH".equals(riskBand)
                || ("MEDIUM".equals(riskBand) && override.requestedDiscountPercent() >= 20.0);

        String summary = String.format(
                Locale.US,
                "Risk %s (%d/100) for requested %.2f%% override.",
                riskBand,
                score,
                override.requestedDiscountPercent());
        String rationale = buildRationale(override, requesterSignal, customerSignal, lookbackDays);
        String recommendedAction = switch (riskBand) {
            case "HIGH" -> "Use strict review: verify customer context, require detailed reason, and escalate to Admin if uncertain.";
            case "MEDIUM" -> "Review requester history and enrollment activity before approval.";
            default -> "Proceed with standard approval checklist.";
        };

        int requesterRecentRequestCount = requesterSignal == null ? 0 : requesterSignal.totalRequests();
        double requesterRecentRejectionRate = requesterSignal == null ? 0.0 : requesterSignal.rejectionRatePercent();
        int customerRecentLifecycleEvents = customerSignal == null ? 0 : customerSignal.totalEvents();

        return new OverrideRiskAssessment(
                override.overrideId(),
                score,
                riskBand,
                escalationRecommended,
                override.requestedDiscountPercent(),
                requesterRecentRequestCount,
                requesterRecentRejectionRate,
                customerRecentLifecycleEvents,
                lookbackDays,
                summary,
                rationale,
                recommendedAction);
    }

    private int scoreRequestedDiscount(double requestedPercent) {
        double safeRequestedPercent = Math.max(0.0, requestedPercent);
        if (safeRequestedPercent >= 35.0) {
            return 35;
        }
        if (safeRequestedPercent >= 25.0) {
            return 26;
        }
        if (safeRequestedPercent >= 18.0) {
            return 18;
        }
        if (safeRequestedPercent >= 12.0) {
            return 10;
        }
        return 4;
    }

    private int scoreRequesterPattern(SubscriptionDAO.OverrideAbuseSignalRow requesterSignal) {
        if (requesterSignal == null) {
            return 0;
        }
        int score = 0;
        score += Math.min(20, requesterSignal.totalRequests() * 2);
        score += Math.min(15, (int) Math.round(requesterSignal.rejectionRatePercent() * 0.25));
        score += Math.min(10, (int) Math.round(Math.max(0.0, requesterSignal.averageRequestedPercent() - 12.0) * 0.7));
        score += Math.min(8, (int) Math.round(Math.max(0.0, requesterSignal.maxRequestedPercent() - 20.0) * 0.5));
        return Math.max(0, Math.min(53, score));
    }

    private int scoreCustomerPattern(SubscriptionDAO.EnrollmentAbuseSignalRow customerSignal) {
        if (customerSignal == null) {
            return 0;
        }
        int score = 0;
        score += Math.min(10, customerSignal.planChangeCount() * 3);
        score += Math.min(8, customerSignal.cancellationCount() * 4);
        score += Math.min(6, customerSignal.backdatedEnrollmentCount() * 4);
        score += Math.min(6, Math.max(0, customerSignal.distinctPlanCount() - 1) * 2);
        return Math.max(0, Math.min(30, score));
    }

    private int scoreReasonQuality(String reason) {
        if (reason == null || reason.isBlank()) {
            return 10;
        }
        String safeReason = reason.trim();
        if (safeReason.length() < 12) {
            return 6;
        }
        if (safeReason.length() < 20) {
            return 3;
        }
        return 0;
    }

    private String buildRationale(
            SubscriptionDiscountOverride override,
            SubscriptionDAO.OverrideAbuseSignalRow requesterSignal,
            SubscriptionDAO.EnrollmentAbuseSignalRow customerSignal,
            int lookbackDays) {
        String requesterPart = requesterSignal == null
                ? "No requester anomaly signal in lookback."
                : String.format(
                        Locale.US,
                        "Requester signal: %d requests, rejection %.1f%%.",
                        requesterSignal.totalRequests(),
                        requesterSignal.rejectionRatePercent());
        String customerPart = customerSignal == null
                ? "No customer lifecycle anomaly signal in lookback."
                : String.format(
                        Locale.US,
                        "Customer signal: %d lifecycle events, plan changes=%d, cancellations=%d.",
                        customerSignal.totalEvents(),
                        customerSignal.planChangeCount(),
                        customerSignal.cancellationCount());
        return String.format(
                Locale.US,
                "Requested %.2f%%. %s %s Window: last %d days.",
                override.requestedDiscountPercent(),
                requesterPart,
                customerPart,
                lookbackDays);
    }

    public record OverrideRiskAssessment(
            int overrideId,
            int riskScore,
            String riskBand,
            boolean escalationRecommended,
            double requestedDiscountPercent,
            int requesterRecentRequestCount,
            double requesterRecentRejectionRatePercent,
            int customerRecentLifecycleEvents,
            int lookbackDays,
            String summary,
            String rationale,
            String recommendedAction) {
    }
}
