package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionDAO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubscriptionDiscountAbuseDetectionEngine {
    public static final int DEFAULT_RESULT_LIMIT = 50;

    public List<AbuseFinding> detect(
            List<SubscriptionDAO.EnrollmentAbuseSignalRow> enrollmentSignals,
            List<SubscriptionDAO.OverrideAbuseSignalRow> overrideSignals,
            List<SubscriptionDAO.PricingIntegrityAlertRow> pricingAlerts,
            LocalDate startDate,
            LocalDate endDate,
            int limit) {
        int safeLimit = normalizeLimit(limit);
        List<AbuseFinding> findings = new ArrayList<>();

        findings.addAll(evaluateEnrollmentSignals(enrollmentSignals));
        findings.addAll(evaluateOverrideSignals(overrideSignals));
        findings.addAll(evaluateBillingSignals(pricingAlerts));

        findings.sort(Comparator
                .comparingInt(AbuseFinding::riskScore).reversed()
                .thenComparing(AbuseFinding::severity, Comparator.comparingInt(this::severityRank).reversed())
                .thenComparing(AbuseFinding::signalType)
                .thenComparing(AbuseFinding::subjectReference, String.CASE_INSENSITIVE_ORDER));

        if (findings.size() > safeLimit) {
            findings = findings.subList(0, safeLimit);
        }
        return List.copyOf(findings);
    }

    private List<AbuseFinding> evaluateEnrollmentSignals(List<SubscriptionDAO.EnrollmentAbuseSignalRow> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        List<AbuseFinding> rows = new ArrayList<>();
        for (SubscriptionDAO.EnrollmentAbuseSignalRow signal : signals) {
            if (signal == null) {
                continue;
            }
            int score = 0;
            score += Math.min(35, signal.totalEvents() * 4);
            score += Math.min(20, signal.planChangeCount() * 5);
            score += Math.min(20, signal.cancellationCount() * 6);
            score += Math.min(15, signal.backdatedEnrollmentCount() * 8);
            score += Math.min(10, Math.max(0, signal.distinctPlanCount() - 1) * 3);
            score = Math.max(0, Math.min(100, score));

            if (score < 35) {
                continue;
            }

            String severity = score >= 75 ? "HIGH" : score >= 50 ? "MEDIUM" : "LOW";
            String summary = String.format(
                    Locale.US,
                    "Enrollment churn pattern: events=%d, plan changes=%d, cancels=%d, backdated=%d, distinct plans=%d.",
                    signal.totalEvents(),
                    signal.planChangeCount(),
                    signal.cancellationCount(),
                    signal.backdatedEnrollmentCount(),
                    signal.distinctPlanCount());
            String threshold = "High when frequent plan changes/cancellations/backdated enrollments occur in short window.";
            String subjectRef = "customer:" + signal.customerId();
            rows.add(new AbuseFinding(
                    "ENROLLMENT_PATTERN",
                    severity,
                    score,
                    subjectRef,
                    summary,
                    threshold,
                    signal.firstEventAt(),
                    signal.latestEventAt()));
        }
        return rows;
    }

    private List<AbuseFinding> evaluateOverrideSignals(List<SubscriptionDAO.OverrideAbuseSignalRow> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        List<AbuseFinding> rows = new ArrayList<>();
        for (SubscriptionDAO.OverrideAbuseSignalRow signal : signals) {
            if (signal == null) {
                continue;
            }
            int score = 0;
            score += Math.min(30, signal.totalRequests() * 4);
            score += Math.min(30, (int) Math.round(signal.rejectionRatePercent() * 0.5));
            score += Math.min(20, (int) Math.round(Math.max(0.0, signal.averageRequestedPercent() - 10.0)));
            score += Math.min(20, (int) Math.round(Math.max(0.0, signal.maxRequestedPercent() - 20.0)));
            score = Math.max(0, Math.min(100, score));

            if (score < 35) {
                continue;
            }

            String severity = score >= 75 || "HIGH".equalsIgnoreCase(signal.severity())
                    ? "HIGH"
                    : score >= 50 ? "MEDIUM" : "LOW";
            String summary = String.format(
                    Locale.US,
                    "Override pattern: requests=%d, rejected=%d (%.1f%%), avg request=%.1f%%, max request=%.1f%%.",
                    signal.totalRequests(),
                    signal.rejectedCount(),
                    signal.rejectionRatePercent(),
                    signal.averageRequestedPercent(),
                    signal.maxRequestedPercent());
            String threshold = "High when request volume or rejection rate is elevated, or requested percentages are unusually high.";
            String username = signal.requestedByUsername() == null || signal.requestedByUsername().isBlank()
                    ? "user-" + signal.requestedByUserId()
                    : signal.requestedByUsername();
            rows.add(new AbuseFinding(
                    "OVERRIDE_PATTERN",
                    severity,
                    score,
                    "user:" + username,
                    summary,
                    threshold,
                    signal.firstRequestAt(),
                    signal.latestRequestAt()));
        }
        return rows;
    }

    private List<AbuseFinding> evaluateBillingSignals(List<SubscriptionDAO.PricingIntegrityAlertRow> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return List.of();
        }

        record BillingBucket(
                int planId,
                String planCode,
                String planName,
                int totalAlerts,
                int highAlerts,
                int mediumAlerts,
                int severeFinancialAlerts,
                String firstBillDate,
                String latestBillDate) {
        }

        Map<Integer, MutableBillingBucket> buckets = new HashMap<>();
        for (SubscriptionDAO.PricingIntegrityAlertRow alert : alerts) {
            if (alert == null) {
                continue;
            }
            int bucketKey = alert.planId();
            MutableBillingBucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> new MutableBillingBucket(
                    alert.planId(),
                    alert.planCode(),
                    alert.planName()));
            bucket.totalAlerts++;
            if ("HIGH".equalsIgnoreCase(alert.severity())) {
                bucket.highAlerts++;
            } else if ("MEDIUM".equalsIgnoreCase(alert.severity())) {
                bucket.mediumAlerts++;
            }
            if ("NEGATIVE_SAVINGS".equalsIgnoreCase(alert.alertCode())
                    || "SAVINGS_EXCEED_GROSS".equalsIgnoreCase(alert.alertCode())
                    || "NEGATIVE_GROSS_BEFORE_DISCOUNT".equalsIgnoreCase(alert.alertCode())) {
                bucket.severeFinancialAlerts++;
            }
            if (bucket.firstBillDate == null || alert.billDate().compareTo(bucket.firstBillDate) < 0) {
                bucket.firstBillDate = alert.billDate();
            }
            if (bucket.latestBillDate == null || alert.billDate().compareTo(bucket.latestBillDate) > 0) {
                bucket.latestBillDate = alert.billDate();
            }
        }

        List<AbuseFinding> rows = new ArrayList<>();
        for (MutableBillingBucket bucket : buckets.values()) {
            int score = 0;
            score += Math.min(40, bucket.totalAlerts * 6);
            score += Math.min(30, bucket.highAlerts * 8);
            score += Math.min(20, bucket.severeFinancialAlerts * 10);
            score += Math.min(10, bucket.mediumAlerts * 2);
            score = Math.max(0, Math.min(100, score));
            if (score < 35) {
                continue;
            }

            String severity = score >= 75 ? "HIGH" : score >= 50 ? "MEDIUM" : "LOW";
            String summary = String.format(
                    Locale.US,
                    "Billing anomaly pattern: alerts=%d (high=%d, medium=%d), severe-financial=%d.",
                    bucket.totalAlerts,
                    bucket.highAlerts,
                    bucket.mediumAlerts,
                    bucket.severeFinancialAlerts);
            String threshold = "High when pricing integrity anomalies accumulate, especially severe financial alert codes.";
            String subjectRef = "plan:" + (bucket.planCode == null ? String.valueOf(bucket.planId) : bucket.planCode);
            rows.add(new AbuseFinding(
                    "BILLING_PATTERN",
                    severity,
                    score,
                    subjectRef,
                    summary,
                    threshold,
                    bucket.firstBillDate,
                    bucket.latestBillDate));
        }
        return rows;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_RESULT_LIMIT;
        }
        return Math.max(1, Math.min(500, limit));
    }

    private int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toUpperCase(Locale.US)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    public record AbuseFinding(
            String signalType,
            String severity,
            int riskScore,
            String subjectReference,
            String summary,
            String thresholdRule,
            String firstObservedAt,
            String latestObservedAt) {
    }

    private static final class MutableBillingBucket {
        private final int planId;
        private final String planCode;
        private final String planName;
        private int totalAlerts;
        private int highAlerts;
        private int mediumAlerts;
        private int severeFinancialAlerts;
        private String firstBillDate;
        private String latestBillDate;

        private MutableBillingBucket(int planId, String planCode, String planName) {
            this.planId = planId;
            this.planCode = planCode;
            this.planName = planName;
        }
    }
}
