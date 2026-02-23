package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SubscriptionModelMonitoringService {
    private static final int DEFAULT_SIGNAL_LIMIT = 500;
    private static final int DEFAULT_ENROLLMENT_LIMIT = 500;

    private final SubscriptionDAO subscriptionDAO;
    private final SubscriptionDiscountAbuseDetectionEngine abuseDetectionEngine;
    private final SubscriptionPlanRecommendationEngine recommendationEngine;

    public SubscriptionModelMonitoringService() {
        this(new SubscriptionDAO());
    }

    public SubscriptionModelMonitoringService(SubscriptionDAO subscriptionDAO) {
        this(
                subscriptionDAO,
                new SubscriptionDiscountAbuseDetectionEngine(),
                new SubscriptionPlanRecommendationEngine());
    }

    SubscriptionModelMonitoringService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionDiscountAbuseDetectionEngine abuseDetectionEngine,
            SubscriptionPlanRecommendationEngine recommendationEngine) {
        this.subscriptionDAO = subscriptionDAO;
        this.abuseDetectionEngine = abuseDetectionEngine;
        this.recommendationEngine = recommendationEngine;
    }

    public MonitoringSnapshot evaluate(LocalDate startDate, LocalDate endDate) {
        LocalDate safeEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate safeStart = startDate == null ? safeEnd.minusDays(29L) : startDate;
        if (safeStart.isAfter(safeEnd)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        AbuseDetectionMonitoring abuseMonitoring = evaluateAbuseDetection(safeStart, safeEnd);
        RecommendationMonitoring recommendationMonitoring = evaluateRecommendationAcceptance(safeStart, safeEnd);
        return new MonitoringSnapshot(abuseMonitoring, recommendationMonitoring);
    }

    private AbuseDetectionMonitoring evaluateAbuseDetection(LocalDate startDate, LocalDate endDate) {
        List<SubscriptionDAO.EnrollmentAbuseSignalRow> enrollmentSignals = subscriptionDAO.getEnrollmentAbuseSignals(
                startDate,
                endDate,
                3);
        List<SubscriptionDAO.OverrideAbuseSignalRow> overrideSignals = subscriptionDAO.getOverrideAbuseSignals(
                startDate,
                endDate,
                3);
        List<SubscriptionDAO.PricingIntegrityAlertRow> pricingAlerts = subscriptionDAO.getPricingIntegrityAlerts(
                startDate,
                endDate,
                DEFAULT_SIGNAL_LIMIT);

        List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> findings = abuseDetectionEngine.detect(
                enrollmentSignals,
                overrideSignals,
                pricingAlerts,
                startDate,
                endDate,
                DEFAULT_SIGNAL_LIMIT);

        Set<String> predictedSubjects = new HashSet<>();
        for (SubscriptionDiscountAbuseDetectionEngine.AbuseFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            // Precision/recall are computed for monitorable subjects that can be confirmed via pilot feedback links.
            if (!"OVERRIDE_PATTERN".equalsIgnoreCase(finding.signalType())
                    && !"BILLING_PATTERN".equalsIgnoreCase(finding.signalType())) {
                continue;
            }
            String subject = normalizeSubjectReference(finding.subjectReference());
            if (!subject.isBlank()) {
                predictedSubjects.add(subject);
            }
        }

        Set<String> actualSubjects = new HashSet<>();
        for (String subject : subscriptionDAO.getConfirmedAbuseMonitoringSubjects(startDate, endDate)) {
            String normalized = normalizeSubjectReference(subject);
            if (!normalized.isBlank()) {
                actualSubjects.add(normalized);
            }
        }

        int truePositiveCount = 0;
        for (String subject : predictedSubjects) {
            if (actualSubjects.contains(subject)) {
                truePositiveCount++;
            }
        }

        int predictedPositiveCount = predictedSubjects.size();
        int actualPositiveCount = actualSubjects.size();
        int falsePositiveCount = Math.max(0, predictedPositiveCount - truePositiveCount);
        int falseNegativeCount = Math.max(0, actualPositiveCount - truePositiveCount);

        double precisionPercent = predictedPositiveCount <= 0
                ? 0.0
                : round2((truePositiveCount * 100.0) / predictedPositiveCount);
        double recallPercent = actualPositiveCount <= 0
                ? 0.0
                : round2((truePositiveCount * 100.0) / actualPositiveCount);

        return new AbuseDetectionMonitoring(
                precisionPercent,
                recallPercent,
                predictedPositiveCount,
                actualPositiveCount,
                truePositiveCount,
                falsePositiveCount,
                falseNegativeCount);
    }

    private RecommendationMonitoring evaluateRecommendationAcceptance(LocalDate startDate, LocalDate endDate) {
        List<SubscriptionDAO.EnrollmentMonitoringDecisionRow> enrollments = subscriptionDAO.getEnrollmentMonitoringDecisions(
                startDate,
                endDate,
                DEFAULT_ENROLLMENT_LIMIT);
        if (enrollments.isEmpty()) {
            return new RecommendationMonitoring(0.0, 0, 0, 0, 0);
        }

        List<SubscriptionPlan> allPlans = subscriptionDAO.getAllPlans();
        List<SubscriptionPlan> activeCandidatePlans = new ArrayList<>();
        Map<Integer, SubscriptionPlan> planById = new HashMap<>();
        for (SubscriptionPlan plan : allPlans) {
            if (plan == null) {
                continue;
            }
            planById.put(plan.planId(), plan);
            if (plan.status() == SubscriptionPlanStatus.ACTIVE || plan.status() == SubscriptionPlanStatus.DRAFT) {
                activeCandidatePlans.add(plan);
            }
        }

        int evaluatedCount = 0;
        int acceptedCount = 0;
        for (SubscriptionDAO.EnrollmentMonitoringDecisionRow enrollment : enrollments) {
            if (enrollment == null || enrollment.customerId() <= 0 || enrollment.enrolledPlanId() <= 0) {
                continue;
            }

            LocalDate referenceDate = resolveReferenceDate(
                    enrollment.enrollmentCreatedAt(),
                    enrollment.enrollmentStartDate(),
                    endDate);

            List<SubscriptionPlanRecommendationEngine.PurchaseEvent> purchaseEvents = new ArrayList<>();
            for (SubscriptionDAO.CustomerPurchaseEvent event : subscriptionDAO.getCustomerPurchaseEvents(
                    enrollment.customerId(),
                    SubscriptionPlanRecommendationEngine.DEFAULT_LOOKBACK_DAYS,
                    referenceDate)) {
                if (event == null) {
                    continue;
                }
                purchaseEvents.add(new SubscriptionPlanRecommendationEngine.PurchaseEvent(
                        event.billDate(),
                        event.billedAmount()));
            }

            List<SubscriptionPlanRecommendationEngine.RefillEvent> refillEvents = new ArrayList<>();
            for (SubscriptionDAO.CustomerRefillEvent event : subscriptionDAO.getCustomerRefillEvents(
                    enrollment.customerId(),
                    SubscriptionPlanRecommendationEngine.DEFAULT_LOOKBACK_DAYS,
                    referenceDate)) {
                if (event == null) {
                    continue;
                }
                refillEvents.add(new SubscriptionPlanRecommendationEngine.RefillEvent(
                        event.medicineId(),
                        event.billDate(),
                        event.quantity(),
                        event.lineAmount()));
            }

            List<SubscriptionPlan> candidatePlans = new ArrayList<>(activeCandidatePlans);
            SubscriptionPlan enrolledPlan = planById.get(enrollment.enrolledPlanId());
            if (enrolledPlan != null && candidatePlans.stream().noneMatch(p -> p.planId() == enrolledPlan.planId())) {
                candidatePlans.add(enrolledPlan);
            }

            SubscriptionPlanRecommendationEngine.RecommendationResult recommendation = recommendationEngine.recommend(
                    candidatePlans,
                    purchaseEvents,
                    refillEvents,
                    SubscriptionPlanRecommendationEngine.DEFAULT_LOOKBACK_DAYS,
                    referenceDate);
            if (recommendation.recommendations() == null || recommendation.recommendations().isEmpty()) {
                continue;
            }

            evaluatedCount++;
            int topPlanId = recommendation.recommendations().get(0).planId();
            if (topPlanId == enrollment.enrolledPlanId()) {
                acceptedCount++;
            }
        }

        int totalCount = enrollments.size();
        int skippedCount = Math.max(0, totalCount - evaluatedCount);
        double acceptanceRatePercent = evaluatedCount <= 0
                ? 0.0
                : round2((acceptedCount * 100.0) / evaluatedCount);

        return new RecommendationMonitoring(
                acceptanceRatePercent,
                acceptedCount,
                evaluatedCount,
                totalCount,
                skippedCount);
    }

    private LocalDate resolveReferenceDate(String createdAt, String startDate, LocalDate fallbackDate) {
        LocalDate created = tryParseDate(createdAt);
        if (created != null) {
            return created;
        }
        LocalDate started = tryParseDate(startDate);
        if (started != null) {
            return started;
        }
        return fallbackDate == null ? LocalDate.now() : fallbackDate;
    }

    private LocalDate tryParseDate(String raw) {
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

    private String normalizeSubjectReference(String subjectReference) {
        if (subjectReference == null) {
            return "";
        }
        return subjectReference.trim().toLowerCase(Locale.US);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record MonitoringSnapshot(
            AbuseDetectionMonitoring abuseDetection,
            RecommendationMonitoring recommendation) {
    }

    public record AbuseDetectionMonitoring(
            double precisionPercent,
            double recallPercent,
            int predictedPositiveCount,
            int actualPositiveCount,
            int truePositiveCount,
            int falsePositiveCount,
            int falseNegativeCount) {
    }

    public record RecommendationMonitoring(
            double acceptanceRatePercent,
            int acceptedCount,
            int evaluatedCount,
            int totalEnrollments,
            int skippedCount) {
    }
}
