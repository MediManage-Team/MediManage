package org.example.MediManage.service;

import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.subscription.SubscriptionAuditChain;
import org.example.MediManage.service.subscription.SubscriptionEligibilityCode;
import org.example.MediManage.service.subscription.SubscriptionEligibilityResult;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class SubscriptionService {
    private static final DateTimeFormatter DB_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ENTITY_SUBSCRIPTION_PLAN = "subscription_plans";
    private static final String ENTITY_PLAN_MEDICINE_RULE = "subscription_plan_medicine_rules";
    private static final String EVENT_PLAN_CREATED = "POLICY_PLAN_CREATED";
    private static final String EVENT_PLAN_UPDATED = "POLICY_PLAN_UPDATED";
    private static final String EVENT_PLAN_STATUS_CHANGED = "POLICY_PLAN_STATUS_CHANGED";
    private static final String EVENT_RULE_UPSERTED = "POLICY_PLAN_MEDICINE_RULE_UPSERTED";
    private static final String EVENT_RULE_DELETED = "POLICY_PLAN_MEDICINE_RULE_DELETED";

    private final SubscriptionDAO subscriptionDAO;

    public SubscriptionService() {
        this(new SubscriptionDAO());
    }

    SubscriptionService(SubscriptionDAO subscriptionDAO) {
        this.subscriptionDAO = subscriptionDAO;
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

        subscriptionDAO.findPlanById(rule.planId())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + rule.planId()));
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
        return subscriptionDAO.createEnrollment(enrollment, currentUserId());
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
    }

    public void cancelEnrollment(int enrollmentId, String reason, Integer approverUserId, String approvalReference)
            throws SQLException {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        CustomerSubscription current = subscriptionDAO.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        if (current.status() == SubscriptionEnrollmentStatus.CANCELLED) {
            return;
        }
        subscriptionDAO.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.CANCELLED,
                "CANCEL",
                reason,
                currentUserId(),
                approverUserId,
                approvalReference);
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
    }

    public List<CustomerSubscription> getCustomerEnrollments(int customerId) {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        return subscriptionDAO.getCustomerEnrollments(customerId);
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
        if (plan.price() < 0.0) {
            throw new IllegalArgumentException("Plan price must be >= 0.");
        }
        if (plan.durationDays() <= 0) {
            throw new IllegalArgumentException("Duration days must be > 0.");
        }
        if (plan.graceDays() < 0) {
            throw new IllegalArgumentException("Grace days must be >= 0.");
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
