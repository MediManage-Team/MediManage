package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.subscription.SubscriptionAIDecisionLogService;
import org.example.MediManage.service.subscription.SubscriptionAuditChain;
import org.example.MediManage.service.subscription.SubscriptionDiscountAbuseDetectionEngine;
import org.example.MediManage.service.subscription.SubscriptionDynamicOfferSuggestionEngine;
import org.example.MediManage.service.subscription.SubscriptionEligibilityCode;
import org.example.MediManage.service.subscription.SubscriptionEligibilityResult;
import org.example.MediManage.service.subscription.SubscriptionPlanRecommendationEngine;
import org.example.MediManage.service.subscription.SubscriptionRenewalPropensityEngine;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionService {
    private static final DateTimeFormatter DB_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final double MIN_PLAN_PRICE = 0.0;
    static final double MAX_PLAN_PRICE = 100_000.0;
    static final int MIN_PLAN_DURATION_DAYS = 7;
    static final int MAX_PLAN_DURATION_DAYS = 365;
    static final int MIN_GRACE_DAYS = 0;
    static final int MAX_GRACE_DAYS = 30;
    private static final int RECOMMENDATION_LOOKBACK_DAYS = SubscriptionPlanRecommendationEngine.DEFAULT_LOOKBACK_DAYS;
    private static final int DEFAULT_RENEWAL_PROPENSITY_WINDOW_DAYS = SubscriptionRenewalPropensityEngine.DEFAULT_RENEWAL_WINDOW_DAYS;
    private static final int MAX_RENEWAL_PROPENSITY_ROWS = 200;
    private static final int DEFAULT_ABUSE_SIGNAL_WINDOW_DAYS = 30;
    private static final int MAX_ABUSE_SIGNAL_ROWS = 500;
    private static final int DEFAULT_DYNAMIC_OFFER_LIMIT = 3;
    private static final int DEFAULT_DYNAMIC_OFFER_RENEWAL_WINDOW_DAYS = 30;
    private static final int DYNAMIC_OFFER_RENEWAL_LOOKUP_LIMIT = 200;
    private static final String ENTITY_SUBSCRIPTION_PLAN = "subscription_plans";
    private static final String ENTITY_PLAN_MEDICINE_RULE = "subscription_plan_medicine_rules";
    private static final String EVENT_PLAN_CREATED = "POLICY_PLAN_CREATED";
    private static final String EVENT_PLAN_UPDATED = "POLICY_PLAN_UPDATED";
    private static final String EVENT_PLAN_STATUS_CHANGED = "POLICY_PLAN_STATUS_CHANGED";
    private static final String EVENT_RULE_UPSERTED = "POLICY_PLAN_MEDICINE_RULE_UPSERTED";
    private static final String EVENT_RULE_DELETED = "POLICY_PLAN_MEDICINE_RULE_DELETED";
    private static final String DECISION_TYPE_PLAN_RECOMMENDATION = "PLAN_RECOMMENDATION";
    private static final String DECISION_TYPE_DYNAMIC_OFFER = "DYNAMIC_OFFER_SUGGESTION";
    private static final String DECISION_TYPE_RENEWAL_RISK = "RENEWAL_CHURN_RISK";
    private static final String DECISION_TYPE_ABUSE_DETECTION = "DISCOUNT_ABUSE_DETECTION";
    private static final String SUBJECT_CUSTOMER = "CUSTOMER";
    private static final String SUBJECT_ENROLLMENT = "ENROLLMENT";
    private static final String SUBJECT_USER = "USER";
    private static final String SUBJECT_PLAN = "PLAN";
    private static final String SUBJECT_SYSTEM = "SYSTEM";
    private static final String MODEL_COMPONENT_PLAN_RECOMMENDATION = "SubscriptionPlanRecommendationEngine";
    private static final String MODEL_COMPONENT_DYNAMIC_OFFER = "SubscriptionDynamicOfferSuggestionEngine";
    private static final String MODEL_COMPONENT_RENEWAL_PROPENSITY = "SubscriptionRenewalPropensityEngine";
    private static final String MODEL_COMPONENT_ABUSE_DETECTION = "SubscriptionDiscountAbuseDetectionEngine";
    private static final String MODEL_VERSION_V1 = "v1";

    private final SubscriptionDAO subscriptionDAO;
    private final SubscriptionPlanRecommendationEngine recommendationEngine;
    private final SubscriptionRenewalPropensityEngine renewalPropensityEngine;
    private final SubscriptionDiscountAbuseDetectionEngine abuseDetectionEngine;
    private final SubscriptionDynamicOfferSuggestionEngine dynamicOfferSuggestionEngine;
    private final SubscriptionAIDecisionLogService aiDecisionLogService;

    public SubscriptionService() {
        this(
                new SubscriptionDAO(),
                new SubscriptionPlanRecommendationEngine(),
                new SubscriptionRenewalPropensityEngine(),
                new SubscriptionDiscountAbuseDetectionEngine(),
                new SubscriptionDynamicOfferSuggestionEngine(),
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionService(SubscriptionDAO subscriptionDAO) {
        this(
                subscriptionDAO,
                new SubscriptionPlanRecommendationEngine(),
                new SubscriptionRenewalPropensityEngine(),
                new SubscriptionDiscountAbuseDetectionEngine(),
                new SubscriptionDynamicOfferSuggestionEngine(),
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionPlanRecommendationEngine recommendationEngine) {
        this(
                subscriptionDAO,
                recommendationEngine,
                new SubscriptionRenewalPropensityEngine(),
                new SubscriptionDiscountAbuseDetectionEngine(),
                new SubscriptionDynamicOfferSuggestionEngine(),
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionPlanRecommendationEngine recommendationEngine,
            SubscriptionRenewalPropensityEngine renewalPropensityEngine) {
        this(
                subscriptionDAO,
                recommendationEngine,
                renewalPropensityEngine,
                new SubscriptionDiscountAbuseDetectionEngine(),
                new SubscriptionDynamicOfferSuggestionEngine(),
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionPlanRecommendationEngine recommendationEngine,
            SubscriptionRenewalPropensityEngine renewalPropensityEngine,
            SubscriptionDiscountAbuseDetectionEngine abuseDetectionEngine) {
        this(
                subscriptionDAO,
                recommendationEngine,
                renewalPropensityEngine,
                abuseDetectionEngine,
                new SubscriptionDynamicOfferSuggestionEngine(),
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionPlanRecommendationEngine recommendationEngine,
            SubscriptionRenewalPropensityEngine renewalPropensityEngine,
            SubscriptionDiscountAbuseDetectionEngine abuseDetectionEngine,
            SubscriptionDynamicOfferSuggestionEngine dynamicOfferSuggestionEngine) {
        this(
                subscriptionDAO,
                recommendationEngine,
                renewalPropensityEngine,
                abuseDetectionEngine,
                dynamicOfferSuggestionEngine,
                SubscriptionAIDecisionLogService.getInstance());
    }

    SubscriptionService(
            SubscriptionDAO subscriptionDAO,
            SubscriptionPlanRecommendationEngine recommendationEngine,
            SubscriptionRenewalPropensityEngine renewalPropensityEngine,
            SubscriptionDiscountAbuseDetectionEngine abuseDetectionEngine,
            SubscriptionDynamicOfferSuggestionEngine dynamicOfferSuggestionEngine,
            SubscriptionAIDecisionLogService aiDecisionLogService) {
        this.subscriptionDAO = subscriptionDAO;
        this.recommendationEngine = recommendationEngine;
        this.renewalPropensityEngine = renewalPropensityEngine;
        this.abuseDetectionEngine = abuseDetectionEngine;
        this.dynamicOfferSuggestionEngine = dynamicOfferSuggestionEngine;
        this.aiDecisionLogService = aiDecisionLogService;
    }

    public int createPlan(SubscriptionPlan plan) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);
        validatePlan(plan, false);
        int actorUserId = currentUserId();
        int createdPlanId = subscriptionDAO.createPlan(plan, actorUserId);
        Optional<SubscriptionPlan> created = subscriptionDAO.findPlanById(createdPlanId);
        appendPolicyAuditLog(
                EVENT_PLAN_CREATED,
                ENTITY_SUBSCRIPTION_PLAN,
                String.valueOf(createdPlanId),
                actorUserId,
                "Plan created",
                null,
                created.map(this::serializePlan).orElse(null));
        DashboardKpiService.invalidateSubscriptionMetrics();
        return createdPlanId;
    }

    public void updatePlan(SubscriptionPlan plan) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);
        validatePlan(plan, true);
        int actorUserId = currentUserId();
        SubscriptionPlan before = subscriptionDAO.findPlanById(plan.planId())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + plan.planId()));
        subscriptionDAO.updatePlan(plan, actorUserId);
        SubscriptionPlan after = subscriptionDAO.findPlanById(plan.planId()).orElse(before);
        appendPolicyAuditLog(
                EVENT_PLAN_UPDATED,
                ENTITY_SUBSCRIPTION_PLAN,
                String.valueOf(plan.planId()),
                actorUserId,
                "Plan updated",
                serializePlan(before),
                serializePlan(after));
        DashboardKpiService.invalidateSubscriptionMetrics();
    }

    public void activatePlan(int planId) throws SQLException {
        setPlanStatus(planId, SubscriptionPlanStatus.ACTIVE);
    }

    public void pausePlan(int planId) throws SQLException {
        setPlanStatus(planId, SubscriptionPlanStatus.PAUSED);
    }

    public void retirePlan(int planId) throws SQLException {
        setPlanStatus(planId, SubscriptionPlanStatus.RETIRED);
    }

    public void setPlanStatus(int planId, SubscriptionPlanStatus status) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);
        if (planId <= 0) {
            throw new IllegalArgumentException("Plan id must be valid.");
        }
        int actorUserId = currentUserId();
        SubscriptionPlan before = subscriptionDAO.findPlanById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        subscriptionDAO.updatePlanStatus(planId, status, actorUserId);
        SubscriptionPlan after = subscriptionDAO.findPlanById(planId).orElse(before.withStatus(status, actorUserId));
        appendPolicyAuditLog(
                EVENT_PLAN_STATUS_CHANGED,
                ENTITY_SUBSCRIPTION_PLAN,
                String.valueOf(planId),
                actorUserId,
                "Plan status changed to " + status.name(),
                serializePlan(before),
                serializePlan(after));
        DashboardKpiService.invalidateSubscriptionMetrics();
    }

    public List<SubscriptionPlan> getPlans() {
        return subscriptionDAO.getAllPlans();
    }

    public Optional<SubscriptionPlan> getPlan(int planId) {
        return subscriptionDAO.findPlanById(planId);
    }

    public List<SubscriptionPlanMedicineRule> getPlanMedicineRules(int planId) {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);
        if (planId <= 0) {
            throw new IllegalArgumentException("Plan id must be valid.");
        }
        return subscriptionDAO.getPlanMedicineRules(planId);
    }

    public void upsertPlanMedicineRule(SubscriptionPlanMedicineRule rule) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);
        validatePlanMedicineRule(rule);
        int actorUserId = currentUserId();

        SubscriptionPlan plan = subscriptionDAO.findPlanById(rule.planId())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + rule.planId()));
        validateRuleAgainstPlan(rule, plan);
        Optional<SubscriptionPlanMedicineRule> before = subscriptionDAO.findPlanMedicineRuleByPlanAndMedicine(
                rule.planId(),
                rule.medicineId());

        subscriptionDAO.upsertPlanMedicineRule(
                rule.planId(),
                rule.medicineId(),
                rule.includeRule(),
                rule.discountPercent(),
                rule.maxDiscountAmount(),
                rule.minMarginPercent(),
                rule.active());
        Optional<SubscriptionPlanMedicineRule> after = subscriptionDAO.findPlanMedicineRuleByPlanAndMedicine(
                rule.planId(),
                rule.medicineId());
        int entityRuleId = after.map(SubscriptionPlanMedicineRule::ruleId).orElse(rule.ruleId());
        appendPolicyAuditLog(
                EVENT_RULE_UPSERTED,
                ENTITY_PLAN_MEDICINE_RULE,
                String.valueOf(entityRuleId),
                actorUserId,
                "Plan medicine rule upserted for plan " + rule.planId() + " medicine " + rule.medicineId(),
                before.map(this::serializeRule).orElse(null),
                after.map(this::serializeRule).orElse(null));
    }

    public void deletePlanMedicineRule(int ruleId) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);
        if (ruleId <= 0) {
            throw new IllegalArgumentException("Rule id must be valid.");
        }
        int actorUserId = currentUserId();
        Optional<SubscriptionPlanMedicineRule> before = subscriptionDAO.findPlanMedicineRuleById(ruleId);
        subscriptionDAO.deletePlanMedicineRule(ruleId);
        appendPolicyAuditLog(
                EVENT_RULE_DELETED,
                ENTITY_PLAN_MEDICINE_RULE,
                String.valueOf(ruleId),
                actorUserId,
                "Plan medicine rule deleted",
                before.map(this::serializeRule).orElse(null),
                null);
    }

    public int enrollCustomer(
            int customerId,
            int planId,
            LocalDate startDate,
            String enrollmentChannel,
            Integer approverUserId,
            String approvalReference) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        if (customerId <= 0 || planId <= 0) {
            throw new IllegalArgumentException("Customer and plan are required.");
        }
        SubscriptionPlan plan = subscriptionDAO.findPlanById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        if (plan.status() != SubscriptionPlanStatus.ACTIVE && plan.status() != SubscriptionPlanStatus.DRAFT) {
            throw new IllegalStateException("Enrollment is allowed only for ACTIVE/DRAFT plans.");
        }

        LocalDate safeStart = startDate == null ? LocalDate.now() : startDate;
        // Backdated enrollments require policy-level authority.
        if (safeStart.isBefore(LocalDate.now())) {
            RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);
        }

        LocalDate endDate = safeStart.plusDays(Math.max(1, plan.durationDays()));
        LocalDate graceEndDate = plan.graceDays() > 0 ? endDate.plusDays(plan.graceDays()) : null;

        CustomerSubscription enrollment = new CustomerSubscription(
                0,
                customerId,
                planId,
                SubscriptionEnrollmentStatus.ACTIVE,
                toDbTimestamp(safeStart),
                toDbTimestamp(endDate),
                graceEndDate == null ? null : toDbTimestamp(graceEndDate),
                enrollmentChannel == null ? "POS" : enrollmentChannel,
                currentUserId(),
                approverUserId,
                approvalReference,
                null,
                null,
                null,
                null);
        int enrollmentId = subscriptionDAO.createEnrollment(enrollment, currentUserId());
        DashboardKpiService.invalidateSubscriptionMetrics();
        return enrollmentId;
    }

    public void renewEnrollment(int enrollmentId, Integer approverUserId, String approvalReference) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        CustomerSubscription enrollment = subscriptionDAO.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        SubscriptionPlan plan = subscriptionDAO.findPlanById(enrollment.planId())
                .orElseThrow(() -> new IllegalStateException("Plan not found for enrollment: " + enrollment.planId()));

        LocalDate newStart = LocalDate.now();
        LocalDate newEnd = newStart.plusDays(Math.max(1, plan.durationDays()));
        LocalDate newGrace = plan.graceDays() > 0 ? newEnd.plusDays(plan.graceDays()) : null;

        subscriptionDAO.renewEnrollment(
                enrollmentId,
                toDbTimestamp(newStart),
                toDbTimestamp(newEnd),
                newGrace == null ? null : toDbTimestamp(newGrace),
                currentUserId(),
                approverUserId,
                approvalReference);
        DashboardKpiService.invalidateSubscriptionMetrics();
    }

    public void freezeEnrollment(int enrollmentId, String reason, Integer approverUserId, String approvalReference)
            throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        CustomerSubscription current = requireEnrollmentStatus(enrollmentId, SubscriptionEnrollmentStatus.ACTIVE);
        if (current == null) {
            return;
        }
        subscriptionDAO.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.FROZEN,
                "FREEZE",
                reason,
                currentUserId(),
                approverUserId,
                approvalReference);
        DashboardKpiService.invalidateSubscriptionMetrics();
    }

    public void unfreezeEnrollment(int enrollmentId, Integer approverUserId, String approvalReference)
            throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        CustomerSubscription current = requireEnrollmentStatus(enrollmentId, SubscriptionEnrollmentStatus.FROZEN);
        if (current == null) {
            return;
        }
        subscriptionDAO.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.ACTIVE,
                "UNFREEZE",
                "Enrollment reactivated",
                currentUserId(),
                approverUserId,
                approvalReference);
        DashboardKpiService.invalidateSubscriptionMetrics();
    }

    public void cancelEnrollment(int enrollmentId, String reason, Integer approverUserId, String approvalReference)
            throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        String safeReason = requireNonBlank(reason, "Cancellation reason is required.");
        CustomerSubscription current = subscriptionDAO.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        if (current.status() == SubscriptionEnrollmentStatus.CANCELLED) {
            return;
        }
        subscriptionDAO.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.CANCELLED,
                "CANCEL",
                safeReason,
                currentUserId(),
                approverUserId,
                approvalReference);
        DashboardKpiService.invalidateSubscriptionMetrics();
    }

    public void changeEnrollmentPlan(
            int enrollmentId,
            int newPlanId,
            String note,
            Integer approverUserId,
            String approvalReference) throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        subscriptionDAO.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        subscriptionDAO.findPlanById(newPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Target plan not found: " + newPlanId));

        subscriptionDAO.changeEnrollmentPlan(
                enrollmentId,
                newPlanId,
                note,
                currentUserId(),
                approverUserId,
                approvalReference);
        DashboardKpiService.invalidateSubscriptionMetrics();
    }

    public List<CustomerSubscription> getCustomerEnrollments(int customerId) {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        return subscriptionDAO.getCustomerEnrollments(customerId);
    }

    public SubscriptionPlanRecommendationEngine.RecommendationResult recommendPlansForCustomer(int customerId) {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        if (customerId <= 0) {
            throw new IllegalArgumentException("Customer id must be valid.");
        }
        return recommendPlansForCustomerInternal(customerId, true);
    }

    public List<SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion> suggestDynamicOffersForCustomer(
            int customerId,
            int limit) {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        if (customerId <= 0) {
            throw new IllegalArgumentException("Customer id must be valid.");
        }

        int safeLimit = limit <= 0 ? DEFAULT_DYNAMIC_OFFER_LIMIT : Math.min(Math.max(1, limit), 20);
        SubscriptionPlanRecommendationEngine.RecommendationResult recommendations =
                recommendPlansForCustomerInternal(customerId, false);
        if (recommendations.recommendations() == null || recommendations.recommendations().isEmpty()) {
            logDynamicOfferDecision(customerId, safeLimit, List.of(), "LOW", 0.0, 0);
            return List.of();
        }

        String riskBand = "LOW";
        double riskScore = 0.0;
        List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> renewalRiskRows = scoreRenewalChurnRiskInternal(
                DEFAULT_DYNAMIC_OFFER_RENEWAL_WINDOW_DAYS,
                DYNAMIC_OFFER_RENEWAL_LOOKUP_LIMIT,
                false);
        for (SubscriptionRenewalPropensityEngine.RenewalPropensityScore riskRow : renewalRiskRows) {
            if (riskRow == null || riskRow.customerId() != customerId) {
                continue;
            }
            riskBand = riskRow.riskBand();
            riskScore = riskRow.churnRiskScore();
            break;
        }

        Map<Integer, SubscriptionPlan> plansById = new HashMap<>();
        for (SubscriptionPlan plan : subscriptionDAO.getAllPlans()) {
            if (plan != null) {
                plansById.put(plan.planId(), plan);
            }
        }

        List<SubscriptionDynamicOfferSuggestionEngine.OfferCandidate> candidates = new ArrayList<>();
        for (SubscriptionPlanRecommendationEngine.PlanRecommendation recommendation : recommendations.recommendations()) {
            if (recommendation == null) {
                continue;
            }
            SubscriptionPlan plan = plansById.get(recommendation.planId());
            if (plan == null) {
                continue;
            }
            candidates.add(new SubscriptionDynamicOfferSuggestionEngine.OfferCandidate(
                    recommendation.planId(),
                    recommendation.planCode(),
                    recommendation.planName(),
                    recommendation.effectiveDiscountPercent(),
                    recommendation.expectedMonthlySavings(),
                    recommendation.expectedMonthlyPlanCost(),
                    plan.maxDiscountPercent(),
                    plan.minimumMarginPercent()));
        }

        List<SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion> suggestions = dynamicOfferSuggestionEngine
                .suggest(candidates, riskBand, riskScore, safeLimit);
        logDynamicOfferDecision(customerId, safeLimit, suggestions, riskBand, riskScore, candidates.size());
        return suggestions;
    }

    public List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> scoreRenewalChurnRisk(
            int renewalWindowDays,
            int limit) {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        return scoreRenewalChurnRiskInternal(renewalWindowDays, limit, true);
    }

    public List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> detectDiscountAbuse(
            LocalDate startDate,
            LocalDate endDate,
            int limit) {
        RbacPolicy.requireCurrentUser(Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        LocalDate safeEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate safeStart = startDate == null ? safeEnd.minusDays(DEFAULT_ABUSE_SIGNAL_WINDOW_DAYS - 1L) : startDate;
        if (safeStart.isAfter(safeEnd)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        int safeLimit = normalizeAbuseDetectionLimit(limit);
        List<SubscriptionDAO.EnrollmentAbuseSignalRow> enrollmentSignals = subscriptionDAO.getEnrollmentAbuseSignals(
                safeStart,
                safeEnd,
                3);
        List<SubscriptionDAO.OverrideAbuseSignalRow> overrideSignals = subscriptionDAO.getOverrideAbuseSignals(
                safeStart,
                safeEnd,
                3);
        List<SubscriptionDAO.PricingIntegrityAlertRow> pricingAlerts = subscriptionDAO.getPricingIntegrityAlerts(
                safeStart,
                safeEnd,
                Math.max(50, safeLimit * 4));

        List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> findings = abuseDetectionEngine.detect(
                enrollmentSignals,
                overrideSignals,
                pricingAlerts,
                safeStart,
                safeEnd,
                safeLimit);
        logAbuseDetectionDecisions(safeStart, safeEnd, safeLimit, findings);
        return findings;
    }

    private SubscriptionPlanRecommendationEngine.RecommendationResult recommendPlansForCustomerInternal(
            int customerId,
            boolean logDecision) {
        List<SubscriptionPlan> candidatePlans = new ArrayList<>();
        for (SubscriptionPlan plan : subscriptionDAO.getAllPlans()) {
            if (plan == null) {
                continue;
            }
            if (plan.status() == SubscriptionPlanStatus.ACTIVE || plan.status() == SubscriptionPlanStatus.DRAFT) {
                candidatePlans.add(plan);
            }
        }

        List<SubscriptionPlanRecommendationEngine.PurchaseEvent> purchaseEvents = new ArrayList<>();
        for (SubscriptionDAO.CustomerPurchaseEvent event : subscriptionDAO.getCustomerPurchaseEvents(
                customerId,
                RECOMMENDATION_LOOKBACK_DAYS)) {
            if (event == null) {
                continue;
            }
            purchaseEvents.add(new SubscriptionPlanRecommendationEngine.PurchaseEvent(
                    event.billDate(),
                    event.billedAmount()));
        }

        List<SubscriptionPlanRecommendationEngine.RefillEvent> refillEvents = new ArrayList<>();
        for (SubscriptionDAO.CustomerRefillEvent event : subscriptionDAO.getCustomerRefillEvents(
                customerId,
                RECOMMENDATION_LOOKBACK_DAYS)) {
            if (event == null) {
                continue;
            }
            refillEvents.add(new SubscriptionPlanRecommendationEngine.RefillEvent(
                    event.medicineId(),
                    event.billDate(),
                    event.quantity(),
                    event.lineAmount()));
        }

        SubscriptionPlanRecommendationEngine.RecommendationResult result = recommendationEngine.recommend(
                candidatePlans,
                purchaseEvents,
                refillEvents,
                RECOMMENDATION_LOOKBACK_DAYS);
        if (logDecision) {
            logPlanRecommendationDecision(customerId, candidatePlans.size(), result);
        }
        return result;
    }

    private List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> scoreRenewalChurnRiskInternal(
            int renewalWindowDays,
            int limit,
            boolean logDecision) {
        int safeLimit = normalizeRenewalRiskLimit(limit);
        int safeWindow = renewalWindowDays <= 0 ? DEFAULT_RENEWAL_PROPENSITY_WINDOW_DAYS : renewalWindowDays;
        List<SubscriptionDAO.RenewalDueCandidate> dueCandidates = subscriptionDAO.getRenewalDueCandidates(safeWindow);
        if (dueCandidates.isEmpty()) {
            if (logDecision) {
                aiDecisionLogService.logDecision(
                        DECISION_TYPE_RENEWAL_RISK,
                        SUBJECT_SYSTEM,
                        "window_days:" + safeWindow,
                        "RENEWAL_NO_CANDIDATES",
                        "No renewal-due candidates found in the selected window.",
                        "{\"renewal_window_days\":" + safeWindow + ",\"result_count\":0}",
                        MODEL_COMPONENT_RENEWAL_PROPENSITY,
                        MODEL_VERSION_V1,
                        null,
                        null,
                        currentUserId());
            }
            return List.of();
        }

        Map<Integer, List<SubscriptionDAO.CustomerPurchaseEvent>> purchaseEventsByCustomerId = new HashMap<>();
        Map<Integer, List<SubscriptionDAO.CustomerRefillEvent>> refillEventsByCustomerId = new HashMap<>();
        Map<Integer, Integer> renewalCountByEnrollmentId = new HashMap<>();
        for (SubscriptionDAO.RenewalDueCandidate candidate : dueCandidates) {
            if (candidate == null) {
                continue;
            }
            purchaseEventsByCustomerId.computeIfAbsent(
                    candidate.customerId(),
                    customerId -> subscriptionDAO.getCustomerPurchaseEvents(customerId, RECOMMENDATION_LOOKBACK_DAYS));
            refillEventsByCustomerId.computeIfAbsent(
                    candidate.customerId(),
                    customerId -> subscriptionDAO.getCustomerRefillEvents(customerId, RECOMMENDATION_LOOKBACK_DAYS));
            renewalCountByEnrollmentId.put(
                    candidate.enrollmentId(),
                    subscriptionDAO.getEnrollmentRenewalCount(candidate.enrollmentId()));
        }

        List<SubscriptionRenewalPropensityEngine.RenewalCandidate> modelCandidates = new ArrayList<>();
        for (SubscriptionDAO.RenewalDueCandidate candidate : dueCandidates) {
            if (candidate == null) {
                continue;
            }

            List<SubscriptionRenewalPropensityEngine.PurchaseEvent> purchases = new ArrayList<>();
            for (SubscriptionDAO.CustomerPurchaseEvent event : purchaseEventsByCustomerId
                    .getOrDefault(candidate.customerId(), List.of())) {
                if (event == null) {
                    continue;
                }
                purchases.add(new SubscriptionRenewalPropensityEngine.PurchaseEvent(
                        event.billDate(),
                        event.billedAmount()));
            }

            List<SubscriptionRenewalPropensityEngine.PurchaseEvent> refills = new ArrayList<>();
            for (SubscriptionDAO.CustomerRefillEvent event : refillEventsByCustomerId
                    .getOrDefault(candidate.customerId(), List.of())) {
                if (event == null) {
                    continue;
                }
                refills.add(new SubscriptionRenewalPropensityEngine.PurchaseEvent(
                        event.billDate(),
                        event.lineAmount()));
            }

            double monthlyPlanCost = round2(Math.max(0.0, candidate.planPrice()) * (30.0 / Math.max(1, candidate.durationDays())));
            modelCandidates.add(new SubscriptionRenewalPropensityEngine.RenewalCandidate(
                    candidate.enrollmentId(),
                    candidate.customerId(),
                    candidate.planId(),
                    candidate.planCode(),
                    candidate.planName(),
                    candidate.startDate(),
                    candidate.endDate(),
                    monthlyPlanCost,
                    renewalCountByEnrollmentId.getOrDefault(candidate.enrollmentId(), 0),
                    purchases,
                    refills));
        }

        List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> scored = renewalPropensityEngine.score(
                modelCandidates,
                safeWindow);
        List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> limitedRows;
        if (scored.size() <= safeLimit) {
            limitedRows = scored;
        } else {
            limitedRows = scored.subList(0, safeLimit);
        }
        if (logDecision) {
            logRenewalRiskDecisions(safeWindow, dueCandidates.size(), limitedRows);
        }
        return limitedRows;
    }

    private void logPlanRecommendationDecision(
            int customerId,
            int candidatePlanCount,
            SubscriptionPlanRecommendationEngine.RecommendationResult result) {
        List<SubscriptionPlanRecommendationEngine.PlanRecommendation> recommendations = result == null
                ? List.of()
                : result.recommendations();
        SubscriptionPlanRecommendationEngine.CustomerBehaviorSnapshot behavior = result == null ? null : result.behavior();
        String reasonCode;
        if (recommendations == null || recommendations.isEmpty()) {
            if (candidatePlanCount <= 0) {
                reasonCode = "PLAN_RECO_NO_ACTIVE_PLANS";
            } else if (behavior == null || behavior.billCount() <= 0) {
                reasonCode = "PLAN_RECO_NO_HISTORY";
            } else {
                reasonCode = "PLAN_RECO_EMPTY_OUTPUT";
            }
        } else {
            SubscriptionPlanRecommendationEngine.PlanRecommendation top = recommendations.get(0);
            if (behavior != null && behavior.confidenceScore() < 0.45) {
                reasonCode = "PLAN_RECO_LOW_CONFIDENCE";
            } else if (top.expectedNetMonthlyBenefit() >= 0.0) {
                reasonCode = "PLAN_RECO_TOP_POSITIVE_BENEFIT";
            } else {
                reasonCode = "PLAN_RECO_TOP_NEGATIVE_BENEFIT";
            }
        }

        String message = result == null || result.statusMessage() == null || result.statusMessage().isBlank()
                ? "Plan recommendation evaluated."
                : result.statusMessage();
        aiDecisionLogService.logDecision(
                DECISION_TYPE_PLAN_RECOMMENDATION,
                SUBJECT_CUSTOMER,
                String.valueOf(customerId),
                reasonCode,
                message,
                buildPlanRecommendationPayload(customerId, candidatePlanCount, result),
                MODEL_COMPONENT_PLAN_RECOMMENDATION,
                MODEL_VERSION_V1,
                null,
                null,
                currentUserId());
    }

    private void logDynamicOfferDecision(
            int customerId,
            int limit,
            List<SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion> offers,
            String riskBand,
            double riskScore,
            int candidateCount) {
        List<SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion> safeOffers = offers == null ? List.of() : offers;
        int clippedCount = 0;
        for (SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion row : safeOffers) {
            if (row != null && row.guardrailCapApplied()) {
                clippedCount++;
            }
        }
        String reasonCode;
        String reasonMessage;
        if (safeOffers.isEmpty()) {
            reasonCode = "DYNAMIC_OFFER_NO_CANDIDATES";
            reasonMessage = "No dynamic offer candidates available.";
        } else if (clippedCount > 0) {
            reasonCode = "DYNAMIC_OFFER_GUARDRAIL_CLIPPED";
            reasonMessage = clippedCount + " offer(s) clipped by configured guardrails.";
        } else {
            reasonCode = "DYNAMIC_OFFER_WITHIN_GUARDRAIL";
            reasonMessage = "Offers generated within configured guardrails.";
        }

        SubscriptionDynamicOfferSuggestionEngine.DynamicOfferSuggestion top = safeOffers.isEmpty() ? null : safeOffers.get(0);
        String payload = "{"
                + "\"customer_id\":" + customerId
                + ",\"limit\":" + limit
                + ",\"candidate_count\":" + candidateCount
                + ",\"offer_count\":" + safeOffers.size()
                + ",\"clipped_offer_count\":" + clippedCount
                + ",\"risk_band\":\"" + json(riskBand) + "\""
                + ",\"risk_score\":" + round2(riskScore)
                + ",\"top_plan_code\":\"" + json(top == null ? "" : top.planCode()) + "\""
                + ",\"top_offer_discount_percent\":" + (top == null ? 0.0 : top.offerDiscountPercent())
                + ",\"top_expected_net_monthly_benefit\":" + (top == null ? 0.0 : top.expectedNetMonthlyBenefit())
                + "}";
        aiDecisionLogService.logDecision(
                DECISION_TYPE_DYNAMIC_OFFER,
                SUBJECT_CUSTOMER,
                String.valueOf(customerId),
                reasonCode,
                reasonMessage,
                payload,
                MODEL_COMPONENT_DYNAMIC_OFFER,
                MODEL_VERSION_V1,
                null,
                null,
                currentUserId());
    }

    private void logRenewalRiskDecisions(
            int renewalWindowDays,
            int dueCandidateCount,
            List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> rows) {
        List<SubscriptionRenewalPropensityEngine.RenewalPropensityScore> safeRows = rows == null ? List.of() : rows;
        if (safeRows.isEmpty()) {
            aiDecisionLogService.logDecision(
                    DECISION_TYPE_RENEWAL_RISK,
                    SUBJECT_SYSTEM,
                    "window_days:" + renewalWindowDays,
                    "RENEWAL_NO_CANDIDATES",
                    "No renewal churn-risk score produced.",
                    "{\"renewal_window_days\":" + renewalWindowDays + ",\"due_candidate_count\":" + dueCandidateCount
                            + ",\"result_count\":0}",
                    MODEL_COMPONENT_RENEWAL_PROPENSITY,
                    MODEL_VERSION_V1,
                    null,
                    null,
                    currentUserId());
            return;
        }

        for (SubscriptionRenewalPropensityEngine.RenewalPropensityScore row : safeRows) {
            if (row == null) {
                continue;
            }
            String riskBand = row.riskBand() == null ? "LOW" : row.riskBand().trim().toUpperCase();
            String reasonCode = switch (riskBand) {
                case "HIGH" -> "RENEWAL_RISK_HIGH";
                case "MEDIUM" -> "RENEWAL_RISK_MEDIUM";
                default -> "RENEWAL_RISK_LOW";
            };
            String message = row.recommendedAction() == null || row.recommendedAction().isBlank()
                    ? "Renewal churn risk scored."
                    : row.recommendedAction();
            String payload = "{"
                    + "\"enrollment_id\":" + row.enrollmentId()
                    + ",\"customer_id\":" + row.customerId()
                    + ",\"plan_code\":\"" + json(row.planCode()) + "\""
                    + ",\"renewal_window_days\":" + renewalWindowDays
                    + ",\"churn_risk_score\":" + row.churnRiskScore()
                    + ",\"churn_probability\":" + row.churnProbability()
                    + ",\"days_until_renewal\":" + row.daysUntilRenewal()
                    + ",\"confidence_score\":" + row.confidenceScore()
                    + ",\"purchases_last_30_days\":" + row.purchasesLast30Days()
                    + ",\"refill_regularity_score\":" + row.refillRegularityScore()
                    + ",\"historical_renewal_count\":" + row.historicalRenewalCount()
                    + "}";
            aiDecisionLogService.logDecision(
                    DECISION_TYPE_RENEWAL_RISK,
                    SUBJECT_ENROLLMENT,
                    String.valueOf(row.enrollmentId()),
                    reasonCode,
                    message,
                    payload,
                    MODEL_COMPONENT_RENEWAL_PROPENSITY,
                    MODEL_VERSION_V1,
                    null,
                    null,
                    currentUserId());
        }
    }

    private void logAbuseDetectionDecisions(
            LocalDate startDate,
            LocalDate endDate,
            int limit,
            List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> findings) {
        List<SubscriptionDiscountAbuseDetectionEngine.AbuseFinding> safeFindings = findings == null ? List.of() : findings;
        if (safeFindings.isEmpty()) {
            aiDecisionLogService.logDecision(
                    DECISION_TYPE_ABUSE_DETECTION,
                    SUBJECT_SYSTEM,
                    "window:" + startDate + "_" + endDate,
                    "ABUSE_NO_FINDINGS",
                    "No abuse findings detected for the requested window.",
                    "{\"start_date\":\"" + startDate + "\",\"end_date\":\"" + endDate + "\",\"limit\":" + limit
                            + ",\"finding_count\":0}",
                    MODEL_COMPONENT_ABUSE_DETECTION,
                    MODEL_VERSION_V1,
                    null,
                    null,
                    currentUserId());
            return;
        }

        for (SubscriptionDiscountAbuseDetectionEngine.AbuseFinding finding : safeFindings) {
            if (finding == null) {
                continue;
            }
            String signalType = normalizeToken(finding.signalType(), "UNKNOWN");
            String severity = normalizeToken(finding.severity(), "UNKNOWN");
            String reasonCode = "ABUSE_" + signalType + "_" + severity;
            String subjectType = subjectTypeFromReference(finding.subjectReference());
            String subjectRef = subjectRefFromReference(finding.subjectReference());
            String payload = "{"
                    + "\"signal_type\":\"" + json(finding.signalType()) + "\""
                    + ",\"severity\":\"" + json(finding.severity()) + "\""
                    + ",\"risk_score\":" + finding.riskScore()
                    + ",\"threshold_rule\":\"" + json(finding.thresholdRule()) + "\""
                    + ",\"first_observed_at\":\"" + json(finding.firstObservedAt()) + "\""
                    + ",\"latest_observed_at\":\"" + json(finding.latestObservedAt()) + "\""
                    + ",\"window_start\":\"" + startDate + "\""
                    + ",\"window_end\":\"" + endDate + "\""
                    + ",\"limit\":" + limit
                    + "}";
            aiDecisionLogService.logDecision(
                    DECISION_TYPE_ABUSE_DETECTION,
                    subjectType,
                    subjectRef,
                    reasonCode,
                    finding.summary() == null || finding.summary().isBlank()
                            ? "Abuse finding detected."
                            : finding.summary(),
                    payload,
                    MODEL_COMPONENT_ABUSE_DETECTION,
                    MODEL_VERSION_V1,
                    null,
                    null,
                    currentUserId());
        }
    }

    private String buildPlanRecommendationPayload(
            int customerId,
            int candidatePlanCount,
            SubscriptionPlanRecommendationEngine.RecommendationResult result) {
        SubscriptionPlanRecommendationEngine.CustomerBehaviorSnapshot behavior = result == null ? null : result.behavior();
        List<SubscriptionPlanRecommendationEngine.PlanRecommendation> rows = result == null || result.recommendations() == null
                ? List.of()
                : result.recommendations();
        SubscriptionPlanRecommendationEngine.PlanRecommendation top = rows.isEmpty() ? null : rows.get(0);
        return "{"
                + "\"customer_id\":" + customerId
                + ",\"candidate_plan_count\":" + candidatePlanCount
                + ",\"recommendation_count\":" + rows.size()
                + ",\"bill_count\":" + (behavior == null ? 0 : behavior.billCount())
                + ",\"confidence_score\":" + (behavior == null ? 0.0 : behavior.confidenceScore())
                + ",\"estimated_monthly_spend\":" + (behavior == null ? 0.0 : behavior.estimatedMonthlySpend())
                + ",\"top_plan_code\":\"" + json(top == null ? "" : top.planCode()) + "\""
                + ",\"top_recommendation_score\":" + (top == null ? 0.0 : top.recommendationScore())
                + ",\"top_expected_monthly_savings\":" + (top == null ? 0.0 : top.expectedMonthlySavings())
                + ",\"top_expected_net_monthly_benefit\":" + (top == null ? 0.0 : top.expectedNetMonthlyBenefit())
                + "}";
    }

    private String subjectTypeFromReference(String subjectReference) {
        if (subjectReference == null || subjectReference.trim().isEmpty()) {
            return SUBJECT_SYSTEM;
        }
        String lower = subjectReference.trim().toLowerCase();
        if (lower.startsWith("customer:")) {
            return SUBJECT_CUSTOMER;
        }
        if (lower.startsWith("user:")) {
            return SUBJECT_USER;
        }
        if (lower.startsWith("plan:")) {
            return SUBJECT_PLAN;
        }
        return SUBJECT_SYSTEM;
    }

    private String subjectRefFromReference(String subjectReference) {
        if (subjectReference == null || subjectReference.trim().isEmpty()) {
            return "n/a";
        }
        int delimiter = subjectReference.indexOf(':');
        if (delimiter < 0 || delimiter + 1 >= subjectReference.length()) {
            return subjectReference.trim();
        }
        return subjectReference.substring(delimiter + 1).trim();
    }

    private String normalizeToken(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        String token = raw.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        token = token.replaceAll("_+", "_");
        if (token.startsWith("_")) {
            token = token.substring(1);
        }
        if (token.endsWith("_")) {
            token = token.substring(0, token.length() - 1);
        }
        return token.isBlank() ? fallback : token;
    }

    public SubscriptionEligibilityResult evaluateEligibility(Integer customerId) {
        if (customerId == null || customerId <= 0) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.NO_CUSTOMER_SELECTED,
                    "No customer selected.");
        }

        Optional<SubscriptionDAO.EligibilityContext> latestContext = subscriptionDAO.findLatestEligibilityContext(customerId);
        if (latestContext.isEmpty()) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.NO_ENROLLMENT,
                    "Customer has no subscription enrollment.");
        }

        SubscriptionDAO.EligibilityContext context = latestContext.get();
        CustomerSubscription enrollment = context.enrollment();
        if (enrollment == null) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE,
                    "Enrollment state is invalid.");
        }

        switch (enrollment.status()) {
            case FROZEN:
                return SubscriptionEligibilityResult.ineligible(
                        SubscriptionEligibilityCode.ENROLLMENT_FROZEN,
                        "Subscription enrollment is frozen.");
            case CANCELLED:
                return SubscriptionEligibilityResult.ineligible(
                        SubscriptionEligibilityCode.ENROLLMENT_CANCELLED,
                        "Subscription enrollment is cancelled.");
            case EXPIRED:
                return SubscriptionEligibilityResult.ineligible(
                        SubscriptionEligibilityCode.ENROLLMENT_EXPIRED,
                        "Subscription enrollment is expired.");
            default:
                break;
        }

        if (context.planStatus() == null) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.PLAN_NOT_FOUND,
                    "Linked subscription plan was not found.");
        }

        if (context.planStatus() != SubscriptionPlanStatus.ACTIVE) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.PLAN_INACTIVE,
                    "Linked subscription plan is not active.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = parseTimestamp(enrollment.startDate());
        LocalDateTime end = parseTimestamp(enrollment.endDate());
        LocalDateTime graceEnd = parseTimestamp(enrollment.graceEndDate());

        if (start != null && now.isBefore(start)) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE,
                    "Subscription enrollment is not yet active.");
        }

        LocalDateTime effectiveEnd = graceEnd != null ? graceEnd : end;
        if (effectiveEnd != null && now.isAfter(effectiveEnd)) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.ENROLLMENT_EXPIRED,
                    "Subscription enrollment has expired.");
        }

        return SubscriptionEligibilityResult.eligible(
                enrollment.enrollmentId(),
                enrollment.planId(),
                context.planName());
    }

    private CustomerSubscription requireEnrollmentStatus(int enrollmentId, SubscriptionEnrollmentStatus expectedStatus)
            throws SQLException {
        CustomerSubscription current = subscriptionDAO.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        if (current.status() != expectedStatus) {
            throw new IllegalStateException("Enrollment must be in status " + expectedStatus + ".");
        }
        return current;
    }

    private void validatePlan(SubscriptionPlan plan, boolean requiresId) {
        if (plan == null) {
            throw new IllegalArgumentException("Plan is required.");
        }
        if (requiresId && plan.planId() <= 0) {
            throw new IllegalArgumentException("Plan id is required.");
        }
        if (plan.planCode() == null || plan.planCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Plan code is required.");
        }
        if (plan.planName() == null || plan.planName().trim().isEmpty()) {
            throw new IllegalArgumentException("Plan name is required.");
        }
        if (plan.price() < MIN_PLAN_PRICE || plan.price() > MAX_PLAN_PRICE) {
            throw new IllegalArgumentException("Plan price must be between " + MIN_PLAN_PRICE + " and " + MAX_PLAN_PRICE + ".");
        }
        if (plan.durationDays() < MIN_PLAN_DURATION_DAYS || plan.durationDays() > MAX_PLAN_DURATION_DAYS) {
            throw new IllegalArgumentException("Duration days must be between " + MIN_PLAN_DURATION_DAYS
                    + " and " + MAX_PLAN_DURATION_DAYS + ".");
        }
        if (plan.graceDays() < MIN_GRACE_DAYS || plan.graceDays() > MAX_GRACE_DAYS) {
            throw new IllegalArgumentException("Grace days must be between " + MIN_GRACE_DAYS + " and " + MAX_GRACE_DAYS + ".");
        }
        if (plan.graceDays() > plan.durationDays()) {
            throw new IllegalArgumentException("Grace days cannot exceed duration days.");
        }
        if (plan.defaultDiscountPercent() < 0.0 || plan.defaultDiscountPercent() > 100.0) {
            throw new IllegalArgumentException("Default discount percent must be between 0 and 100.");
        }
        if (plan.maxDiscountPercent() < 0.0 || plan.maxDiscountPercent() > 100.0) {
            throw new IllegalArgumentException("Max discount percent must be between 0 and 100.");
        }
        if (plan.minimumMarginPercent() < 0.0 || plan.minimumMarginPercent() > 100.0) {
            throw new IllegalArgumentException("Minimum margin percent must be between 0 and 100.");
        }
    }

    private void validatePlanMedicineRule(SubscriptionPlanMedicineRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Plan medicine rule is required.");
        }
        if (rule.planId() <= 0) {
            throw new IllegalArgumentException("Plan id is required.");
        }
        if (rule.medicineId() <= 0) {
            throw new IllegalArgumentException("Medicine id is required.");
        }
        if (rule.discountPercent() < 0.0 || rule.discountPercent() > 100.0) {
            throw new IllegalArgumentException("Rule discount percent must be between 0 and 100.");
        }
        if (rule.maxDiscountAmount() != null && rule.maxDiscountAmount() < 0.0) {
            throw new IllegalArgumentException("Rule max discount amount must be >= 0.");
        }
        if (rule.minMarginPercent() != null
                && (rule.minMarginPercent() < 0.0 || rule.minMarginPercent() > 100.0)) {
            throw new IllegalArgumentException("Rule minimum margin percent must be between 0 and 100.");
        }
    }

    private void validateRuleAgainstPlan(SubscriptionPlanMedicineRule rule, SubscriptionPlan plan) {
        if (rule == null || plan == null) {
            return;
        }
        if (rule.discountPercent() > plan.maxDiscountPercent()) {
            throw new IllegalArgumentException("Rule discount percent cannot exceed plan max discount percent.");
        }
        if (rule.minMarginPercent() != null && rule.minMarginPercent() < plan.minimumMarginPercent()) {
            throw new IllegalArgumentException("Rule minimum margin percent cannot be below plan minimum margin percent.");
        }
    }

    private void appendPolicyAuditLog(
            String eventType,
            String entityType,
            String entityId,
            int actorUserId,
            String reason,
            String beforeJson,
            String afterJson) throws SQLException {
        String previousChecksum = subscriptionDAO.latestAuditChecksum().orElse("");
        String eventTimestamp = SubscriptionAuditChain.nowTimestamp();
        String checksum = SubscriptionAuditChain.computeChecksum(
                eventType,
                entityType,
                entityId,
                actorUserId,
                null,
                reason,
                beforeJson,
                afterJson,
                previousChecksum,
                eventTimestamp);

        subscriptionDAO.appendSubscriptionAuditLog(
                eventType,
                entityType,
                entityId,
                actorUserId,
                null,
                reason,
                beforeJson,
                afterJson,
                previousChecksum.isBlank() ? null : previousChecksum,
                checksum);
    }

    private String serializePlan(SubscriptionPlan plan) {
        if (plan == null) {
            return null;
        }
        return "{"
                + "\"plan_id\":" + plan.planId()
                + ",\"plan_code\":\"" + json(plan.planCode()) + "\""
                + ",\"plan_name\":\"" + json(plan.planName()) + "\""
                + ",\"price\":" + plan.price()
                + ",\"duration_days\":" + plan.durationDays()
                + ",\"grace_days\":" + plan.graceDays()
                + ",\"default_discount_percent\":" + plan.defaultDiscountPercent()
                + ",\"max_discount_percent\":" + plan.maxDiscountPercent()
                + ",\"minimum_margin_percent\":" + plan.minimumMarginPercent()
                + ",\"status\":\"" + (plan.status() == null ? "" : plan.status().name()) + "\""
                + ",\"auto_renew\":" + plan.autoRenew()
                + ",\"requires_approval\":" + plan.requiresApproval()
                + "}";
    }

    private String serializeRule(SubscriptionPlanMedicineRule rule) {
        if (rule == null) {
            return null;
        }
        return "{"
                + "\"rule_id\":" + rule.ruleId()
                + ",\"plan_id\":" + rule.planId()
                + ",\"medicine_id\":" + rule.medicineId()
                + ",\"medicine_name\":\"" + json(rule.medicineName()) + "\""
                + ",\"include_rule\":" + rule.includeRule()
                + ",\"discount_percent\":" + rule.discountPercent()
                + ",\"max_discount_amount\":" + (rule.maxDiscountAmount() == null ? "null" : rule.maxDiscountAmount())
                + ",\"min_margin_percent\":" + (rule.minMarginPercent() == null ? "null" : rule.minMarginPercent())
                + ",\"active\":" + rule.active()
                + "}";
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String requireNonBlank(String raw, String message) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return raw.trim();
    }

    private int normalizeRenewalRiskLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.max(1, Math.min(MAX_RENEWAL_PROPENSITY_ROWS, limit));
    }

    private int normalizeAbuseDetectionLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.max(1, Math.min(MAX_ABUSE_SIGNAL_ROWS, limit));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int currentUserId() {
        if (!UserSession.getInstance().isLoggedIn() || UserSession.getInstance().getUser() == null) {
            throw new SecurityException("Access denied: login required.");
        }
        return UserSession.getInstance().getUser().getId();
    }

    private String toDbTimestamp(LocalDate date) {
        return date.atStartOfDay().format(DB_TIMESTAMP);
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), DB_TIMESTAMP);
        } catch (Exception e) {
            return null;
        }
    }
}
