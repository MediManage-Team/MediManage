package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionApproval;
import org.example.MediManage.model.SubscriptionApprovalStatus;
import org.example.MediManage.model.SubscriptionDiscountOverride;
import org.example.MediManage.model.SubscriptionDiscountOverrideStatus;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.service.subscription.SubscriptionDiscountEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionDAO {
    private static final DateTimeFormatter DB_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int createPlan(SubscriptionPlan plan, int actorUserId) throws SQLException {
        String sql = "INSERT INTO subscription_plans (" +
                "plan_code, plan_name, description, price, duration_days, grace_days, " +
                "default_discount_percent, max_discount_percent, minimum_margin_percent, status, " +
                "auto_renew, requires_approval, created_by_user_id, updated_by_user_id" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, plan.planCode());
            ps.setString(2, plan.planName());
            ps.setString(3, plan.description());
            ps.setDouble(4, plan.price());
            ps.setInt(5, plan.durationDays());
            ps.setInt(6, plan.graceDays());
            ps.setDouble(7, plan.defaultDiscountPercent());
            ps.setDouble(8, plan.maxDiscountPercent());
            ps.setDouble(9, plan.minimumMarginPercent());
            ps.setString(10, normalizePlanStatus(plan.status()).name());
            ps.setBoolean(11, plan.autoRenew());
            ps.setBoolean(12, plan.requiresApproval());
            ps.setInt(13, actorUserId);
            ps.setInt(14, actorUserId);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create subscription plan.");
    }

    public void updatePlan(SubscriptionPlan plan, int actorUserId) throws SQLException {
        String sql = "UPDATE subscription_plans SET " +
                "plan_code = ?, plan_name = ?, description = ?, price = ?, duration_days = ?, grace_days = ?, " +
                "default_discount_percent = ?, max_discount_percent = ?, minimum_margin_percent = ?, " +
                "status = ?, auto_renew = ?, requires_approval = ?, " +
                "updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE plan_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, plan.planCode());
            ps.setString(2, plan.planName());
            ps.setString(3, plan.description());
            ps.setDouble(4, plan.price());
            ps.setInt(5, plan.durationDays());
            ps.setInt(6, plan.graceDays());
            ps.setDouble(7, plan.defaultDiscountPercent());
            ps.setDouble(8, plan.maxDiscountPercent());
            ps.setDouble(9, plan.minimumMarginPercent());
            ps.setString(10, normalizePlanStatus(plan.status()).name());
            ps.setBoolean(11, plan.autoRenew());
            ps.setBoolean(12, plan.requiresApproval());
            ps.setInt(13, actorUserId);
            ps.setInt(14, plan.planId());
            ps.executeUpdate();
        }
    }

    public void updatePlanStatus(int planId, SubscriptionPlanStatus status, int actorUserId) throws SQLException {
        String sql = "UPDATE subscription_plans SET status = ?, updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE plan_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizePlanStatus(status).name());
            ps.setInt(2, actorUserId);
            ps.setInt(3, planId);
            ps.executeUpdate();
        }
    }

    public List<SubscriptionPlan> getAllPlans() {
        List<SubscriptionPlan> plans = new ArrayList<>();
        String sql = "SELECT * FROM subscription_plans ORDER BY created_at DESC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                plans.add(mapPlan(rs));
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch subscription plans: " + e.getMessage());
        }
        return plans;
    }

    public Optional<SubscriptionPlan> findPlanById(int planId) {
        String sql = "SELECT * FROM subscription_plans WHERE plan_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPlan(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch subscription plan " + planId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public int createEnrollment(CustomerSubscription enrollment, int actorUserId) throws SQLException {
        String sql = "INSERT INTO customer_subscriptions (" +
                "customer_id, plan_id, status, start_date, end_date, grace_end_date, " +
                "enrollment_channel, enrolled_by_user_id, approved_by_user_id, approval_reference, " +
                "cancellation_reason, frozen_reason" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, enrollment.customerId());
                ps.setInt(2, enrollment.planId());
                ps.setString(3, normalizeEnrollmentStatus(enrollment.status()).name());
                ps.setString(4, enrollment.startDate());
                ps.setString(5, enrollment.endDate());
                ps.setString(6, enrollment.graceEndDate());
                ps.setString(7, enrollment.enrollmentChannel());
                ps.setInt(8, actorUserId);
                ps.setObject(9, enrollment.approvedByUserId());
                ps.setString(10, enrollment.approvalReference());
                ps.setString(11, enrollment.cancellationReason());
                ps.setString(12, enrollment.frozenReason());
                ps.executeUpdate();

                int enrollmentId;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Failed to create customer subscription.");
                    }
                    enrollmentId = rs.getInt(1);
                }

                recordEnrollmentEvent(
                        conn,
                        enrollmentId,
                        "ENROLL",
                        null,
                        enrollment.planId(),
                        "Enrollment created",
                        actorUserId,
                        enrollment.approvedByUserId(),
                        enrollment.approvalReference());

                conn.commit();
                return enrollmentId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void renewEnrollment(
            int enrollmentId,
            String newStartDate,
            String newEndDate,
            String newGraceEndDate,
            int actorUserId,
            Integer approverUserId,
            String approvalReference) throws SQLException {
        String fetch = "SELECT plan_id FROM customer_subscriptions WHERE enrollment_id = ?";
        String update = "UPDATE customer_subscriptions SET " +
                "status = 'ACTIVE', start_date = ?, end_date = ?, grace_end_date = ?, " +
                "cancellation_reason = NULL, frozen_reason = NULL, approved_by_user_id = ?, " +
                "approval_reference = ?, updated_at = CURRENT_TIMESTAMP WHERE enrollment_id = ?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int planId;
                try (PreparedStatement fetchPs = conn.prepareStatement(fetch)) {
                    fetchPs.setInt(1, enrollmentId);
                    try (ResultSet rs = fetchPs.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Enrollment not found: " + enrollmentId);
                        }
                        planId = rs.getInt("plan_id");
                    }
                }

                try (PreparedStatement updatePs = conn.prepareStatement(update)) {
                    updatePs.setString(1, newStartDate);
                    updatePs.setString(2, newEndDate);
                    updatePs.setString(3, newGraceEndDate);
                    updatePs.setObject(4, approverUserId);
                    updatePs.setString(5, approvalReference);
                    updatePs.setInt(6, enrollmentId);
                    updatePs.executeUpdate();
                }

                recordEnrollmentEvent(
                        conn,
                        enrollmentId,
                        "RENEW",
                        planId,
                        planId,
                        "Enrollment renewed",
                        actorUserId,
                        approverUserId,
                        approvalReference);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void changeEnrollmentPlan(
            int enrollmentId,
            int newPlanId,
            String note,
            int actorUserId,
            Integer approverUserId,
            String approvalReference) throws SQLException {
        String fetch = "SELECT plan_id FROM customer_subscriptions WHERE enrollment_id = ?";
        String update = "UPDATE customer_subscriptions SET plan_id = ?, approved_by_user_id = ?, approval_reference = ?, updated_at = CURRENT_TIMESTAMP WHERE enrollment_id = ?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int oldPlanId;
                try (PreparedStatement fetchPs = conn.prepareStatement(fetch)) {
                    fetchPs.setInt(1, enrollmentId);
                    try (ResultSet rs = fetchPs.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Enrollment not found: " + enrollmentId);
                        }
                        oldPlanId = rs.getInt("plan_id");
                    }
                }

                try (PreparedStatement updatePs = conn.prepareStatement(update)) {
                    updatePs.setInt(1, newPlanId);
                    updatePs.setObject(2, approverUserId);
                    updatePs.setString(3, approvalReference);
                    updatePs.setInt(4, enrollmentId);
                    updatePs.executeUpdate();
                }

                recordEnrollmentEvent(
                        conn,
                        enrollmentId,
                        "PLAN_CHANGE",
                        oldPlanId,
                        newPlanId,
                        note == null || note.isBlank() ? "Plan changed" : note,
                        actorUserId,
                        approverUserId,
                        approvalReference);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void updateEnrollmentStatus(
            int enrollmentId,
            SubscriptionEnrollmentStatus status,
            String eventType,
            String reason,
            int actorUserId,
            Integer approverUserId,
            String approvalReference) throws SQLException {
        String fetch = "SELECT plan_id FROM customer_subscriptions WHERE enrollment_id = ?";
        String update = "UPDATE customer_subscriptions SET " +
                "status = ?, cancellation_reason = ?, frozen_reason = ?, " +
                "approved_by_user_id = ?, approval_reference = ?, updated_at = CURRENT_TIMESTAMP WHERE enrollment_id = ?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int planId;
                try (PreparedStatement fetchPs = conn.prepareStatement(fetch)) {
                    fetchPs.setInt(1, enrollmentId);
                    try (ResultSet rs = fetchPs.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Enrollment not found: " + enrollmentId);
                        }
                        planId = rs.getInt("plan_id");
                    }
                }

                String normalizedReason = reason == null ? null : reason.trim();
                String cancellationReason = status == SubscriptionEnrollmentStatus.CANCELLED ? normalizedReason : null;
                String frozenReason = status == SubscriptionEnrollmentStatus.FROZEN ? normalizedReason : null;

                try (PreparedStatement updatePs = conn.prepareStatement(update)) {
                    updatePs.setString(1, normalizeEnrollmentStatus(status).name());
                    updatePs.setString(2, cancellationReason);
                    updatePs.setString(3, frozenReason);
                    updatePs.setObject(4, approverUserId);
                    updatePs.setString(5, approvalReference);
                    updatePs.setInt(6, enrollmentId);
                    updatePs.executeUpdate();
                }

                String safeEventType = eventType == null || eventType.isBlank()
                        ? "STATUS_CHANGE"
                        : eventType.trim().toUpperCase();
                recordEnrollmentEvent(
                        conn,
                        enrollmentId,
                        safeEventType,
                        planId,
                        planId,
                        normalizedReason,
                        actorUserId,
                        approverUserId,
                        approvalReference);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public List<CustomerSubscription> getCustomerEnrollments(int customerId) {
        List<CustomerSubscription> enrollments = new ArrayList<>();
        String sql = "SELECT * FROM customer_subscriptions WHERE customer_id = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    enrollments.add(mapEnrollment(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch customer subscriptions for customer " + customerId + ": " + e.getMessage());
        }
        return enrollments;
    }

    public Optional<CustomerSubscription> findEnrollmentById(int enrollmentId) {
        String sql = "SELECT * FROM customer_subscriptions WHERE enrollment_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapEnrollment(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch customer subscription " + enrollmentId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<EligibilityContext> findLatestEligibilityContext(int customerId) {
        String sql = "SELECT cs.*, sp.plan_name, sp.status AS plan_status " +
                "FROM customer_subscriptions cs " +
                "JOIN subscription_plans sp ON sp.plan_id = cs.plan_id " +
                "WHERE cs.customer_id = ? " +
                "ORDER BY cs.end_date DESC, cs.created_at DESC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CustomerSubscription enrollment = mapEnrollment(rs);
                    return Optional.of(new EligibilityContext(
                            enrollment,
                            SubscriptionPlanStatus.fromString(rs.getString("plan_status")),
                            rs.getString("plan_name")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch latest eligibility context for customer " + customerId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public int createApproval(
            String approvalType,
            String requestRefType,
            int requestRefId,
            int requestedByUserId,
            String reason) throws SQLException {
        String sql = "INSERT INTO subscription_approvals (" +
                "approval_type, request_ref_type, request_ref_id, requested_by_user_id, approval_status, reason" +
                ") VALUES (?, ?, ?, ?, 'PENDING', ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, approvalType);
            ps.setString(2, requestRefType);
            ps.setInt(3, requestRefId);
            ps.setInt(4, requestedByUserId);
            ps.setString(5, reason);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create subscription approval.");
    }

    public void updateApprovalStatus(
            int approvalId,
            SubscriptionApprovalStatus status,
            Integer approverUserId) throws SQLException {
        String sql = "UPDATE subscription_approvals SET approval_status = ?, approver_user_id = ?, approved_at = CURRENT_TIMESTAMP WHERE approval_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setObject(2, approverUserId);
            ps.setInt(3, approvalId);
            ps.executeUpdate();
        }
    }

    public Optional<SubscriptionApproval> findApprovalById(int approvalId) {
        String sql = "SELECT * FROM subscription_approvals WHERE approval_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, approvalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapApproval(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch subscription approval " + approvalId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public int createDiscountOverride(
            Integer billId,
            Integer billItemId,
            Integer customerId,
            Integer enrollmentId,
            double requestedDiscountPercent,
            String reason,
            int requestedByUserId,
            Integer approvalId) throws SQLException {
        String sql = "INSERT INTO subscription_discount_overrides (" +
                "bill_id, bill_item_id, customer_id, enrollment_id, requested_discount_percent, status, reason, requested_by_user_id, approval_id" +
                ") VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, billId);
            ps.setObject(2, billItemId);
            ps.setObject(3, customerId);
            ps.setObject(4, enrollmentId);
            ps.setDouble(5, requestedDiscountPercent);
            ps.setString(6, reason);
            ps.setInt(7, requestedByUserId);
            ps.setObject(8, approvalId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create subscription override.");
    }

    public void updateDiscountOverrideStatus(
            int overrideId,
            SubscriptionDiscountOverrideStatus status,
            Double approvedDiscountPercent,
            Integer approvedByUserId,
            Integer approvalId) throws SQLException {
        String sql = "UPDATE subscription_discount_overrides SET " +
                "status = ?, approved_discount_percent = ?, approved_by_user_id = ?, approval_id = ?, approved_at = CURRENT_TIMESTAMP " +
                "WHERE override_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setObject(2, approvedDiscountPercent);
            ps.setObject(3, approvedByUserId);
            ps.setObject(4, approvalId);
            ps.setInt(5, overrideId);
            ps.executeUpdate();
        }
    }

    public void attachApprovalToOverride(int overrideId, int approvalId) throws SQLException {
        String sql = "UPDATE subscription_discount_overrides SET approval_id = ? WHERE override_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, approvalId);
            ps.setInt(2, overrideId);
            ps.executeUpdate();
        }
    }

    public Optional<SubscriptionDiscountOverride> findDiscountOverrideById(int overrideId) {
        String sql = "SELECT * FROM subscription_discount_overrides WHERE override_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, overrideId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDiscountOverride(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch subscription override " + overrideId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<SubscriptionDiscountOverride> getPendingDiscountOverrides() {
        List<SubscriptionDiscountOverride> rows = new ArrayList<>();
        String sql = "SELECT * FROM subscription_discount_overrides WHERE status = 'PENDING' ORDER BY created_at DESC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapDiscountOverride(rs));
            }
        } catch (Exception e) {
            System.err.println("Failed to list pending overrides: " + e.getMessage());
        }
        return rows;
    }

    public List<SubscriptionPlanMedicineRule> getPlanMedicineRules(int planId) {
        List<SubscriptionPlanMedicineRule> rows = new ArrayList<>();
        String sql = "SELECT r.*, m.name AS medicine_name " +
                "FROM subscription_plan_medicine_rules r " +
                "JOIN medicines m ON m.medicine_id = r.medicine_id " +
                "WHERE r.plan_id = ? " +
                "ORDER BY r.updated_at DESC, r.rule_id DESC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapPlanMedicineRule(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to list plan medicine rules for plan " + planId + ": " + e.getMessage());
        }
        return rows;
    }

    public Optional<SubscriptionPlanMedicineRule> findPlanMedicineRuleByPlanAndMedicine(int planId, int medicineId) {
        String sql = "SELECT r.*, m.name AS medicine_name " +
                "FROM subscription_plan_medicine_rules r " +
                "JOIN medicines m ON m.medicine_id = r.medicine_id " +
                "WHERE r.plan_id = ? AND r.medicine_id = ? " +
                "ORDER BY r.rule_id DESC LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, planId);
            ps.setInt(2, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPlanMedicineRule(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch plan medicine rule for plan " + planId + " medicine " + medicineId
                    + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<SubscriptionPlanMedicineRule> findPlanMedicineRuleById(int ruleId) {
        String sql = "SELECT r.*, m.name AS medicine_name " +
                "FROM subscription_plan_medicine_rules r " +
                "JOIN medicines m ON m.medicine_id = r.medicine_id " +
                "WHERE r.rule_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPlanMedicineRule(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch plan medicine rule " + ruleId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public void upsertPlanMedicineRule(
            int planId,
            int medicineId,
            boolean includeRule,
            double discountPercent,
            Double maxDiscountAmount,
            Double minMarginPercent,
            boolean active) throws SQLException {
        String findSql = "SELECT rule_id FROM subscription_plan_medicine_rules WHERE plan_id = ? AND medicine_id = ? ORDER BY rule_id DESC LIMIT 1";
        String updateSql = "UPDATE subscription_plan_medicine_rules SET " +
                "include_rule = ?, discount_percent = ?, max_discount_amount = ?, min_margin_percent = ?, " +
                "active = ?, updated_at = CURRENT_TIMESTAMP WHERE rule_id = ?";
        String insertSql = "INSERT INTO subscription_plan_medicine_rules (" +
                "plan_id, medicine_id, include_rule, discount_percent, max_discount_amount, min_margin_percent, active" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer ruleId = null;
                try (PreparedStatement findPs = conn.prepareStatement(findSql)) {
                    findPs.setInt(1, planId);
                    findPs.setInt(2, medicineId);
                    try (ResultSet rs = findPs.executeQuery()) {
                        if (rs.next()) {
                            ruleId = rs.getInt("rule_id");
                        }
                    }
                }

                if (ruleId != null) {
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setBoolean(1, includeRule);
                        updatePs.setDouble(2, discountPercent);
                        updatePs.setObject(3, maxDiscountAmount);
                        updatePs.setObject(4, minMarginPercent);
                        updatePs.setBoolean(5, active);
                        updatePs.setInt(6, ruleId);
                        updatePs.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                        insertPs.setInt(1, planId);
                        insertPs.setInt(2, medicineId);
                        insertPs.setBoolean(3, includeRule);
                        insertPs.setDouble(4, discountPercent);
                        insertPs.setObject(5, maxDiscountAmount);
                        insertPs.setObject(6, minMarginPercent);
                        insertPs.setBoolean(7, active);
                        insertPs.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void deletePlanMedicineRule(int ruleId) throws SQLException {
        String sql = "DELETE FROM subscription_plan_medicine_rules WHERE rule_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            ps.executeUpdate();
        }
    }

    public void appendSubscriptionAuditLog(
            String eventType,
            String entityType,
            String entityId,
            Integer actorUserId,
            Integer approvalId,
            String reason,
            String beforeJson,
            String afterJson,
            String previousChecksum,
            String checksum) throws SQLException {
        String sql = "INSERT INTO subscription_audit_log (" +
                "event_type, entity_type, entity_id, actor_user_id, approval_id, reason, before_json, after_json, previous_checksum, checksum" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, entityType);
            ps.setString(3, entityId);
            ps.setObject(4, actorUserId);
            ps.setObject(5, approvalId);
            ps.setString(6, reason);
            ps.setString(7, beforeJson);
            ps.setString(8, afterJson);
            ps.setString(9, previousChecksum);
            ps.setString(10, checksum);
            ps.executeUpdate();
        }
    }

    public Optional<String> latestAuditChecksum() {
        String sql = "SELECT checksum FROM subscription_audit_log ORDER BY audit_id DESC LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.ofNullable(rs.getString("checksum"));
            }
        } catch (Exception e) {
            System.err.println("Failed to read latest subscription audit checksum: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<ApplicableSubscription> findApplicableSubscription(int customerId) {
        String now = LocalDateTime.now().format(DB_TIMESTAMP);

        String sql = "SELECT cs.enrollment_id, cs.plan_id, cs.approval_reference, " +
                "sp.plan_code, sp.plan_name, sp.default_discount_percent, sp.max_discount_percent, sp.minimum_margin_percent " +
                "FROM customer_subscriptions cs " +
                "JOIN subscription_plans sp ON sp.plan_id = cs.plan_id " +
                "WHERE cs.customer_id = ? " +
                "AND cs.status = 'ACTIVE' " +
                "AND sp.status = 'ACTIVE' " +
                "AND cs.start_date <= ? " +
                "AND (cs.end_date >= ? OR (cs.grace_end_date IS NOT NULL AND cs.grace_end_date >= ?)) " +
                "ORDER BY cs.end_date DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            ps.setString(2, now);
            ps.setString(3, now);
            ps.setString(4, now);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ApplicableSubscription(
                            rs.getInt("enrollment_id"),
                            rs.getInt("plan_id"),
                            rs.getString("plan_code"),
                            rs.getString("plan_name"),
                            rs.getDouble("default_discount_percent"),
                            rs.getDouble("max_discount_percent"),
                            rs.getDouble("minimum_margin_percent"),
                            rs.getString("approval_reference")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to find applicable subscription for customer " + customerId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public Map<Integer, SubscriptionDiscountEngine.DiscountRule> loadMedicineRules(int planId) {
        Map<Integer, SubscriptionDiscountEngine.DiscountRule> rules = new HashMap<>();
        String sql = "SELECT medicine_id, include_rule, discount_percent, max_discount_amount, min_margin_percent, active " +
                "FROM subscription_plan_medicine_rules WHERE plan_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int medicineId = rs.getInt("medicine_id");
                    boolean includeRule = rs.getBoolean("include_rule");
                    Double discountPercent = nullableDouble(rs, "discount_percent");
                    Double maxDiscountAmount = nullableDouble(rs, "max_discount_amount");
                    Double minMarginPercent = nullableDouble(rs, "min_margin_percent");
                    boolean active = rs.getBoolean("active");

                    rules.put(medicineId, new SubscriptionDiscountEngine.DiscountRule(
                            includeRule,
                            discountPercent,
                            maxDiscountAmount,
                            minMarginPercent,
                            active));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load subscription medicine rules for plan " + planId + ": " + e.getMessage());
        }
        return rules;
    }

    private void recordEnrollmentEvent(
            Connection conn,
            int enrollmentId,
            String eventType,
            Integer oldPlanId,
            Integer newPlanId,
            String eventNote,
            Integer actorUserId,
            Integer approverUserId,
            String approvalReference) throws SQLException {
        String sql = "INSERT INTO customer_subscription_events (" +
                "enrollment_id, event_type, old_plan_id, new_plan_id, event_note, effective_at, " +
                "created_by_user_id, approved_by_user_id, approval_reference" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            ps.setString(2, eventType);
            ps.setObject(3, oldPlanId);
            ps.setObject(4, newPlanId);
            ps.setString(5, eventNote);
            ps.setString(6, LocalDateTime.now().format(DB_TIMESTAMP));
            ps.setObject(7, actorUserId);
            ps.setObject(8, approverUserId);
            ps.setString(9, approvalReference);
            ps.executeUpdate();
        }
    }

    private SubscriptionPlan mapPlan(ResultSet rs) throws SQLException {
        Integer createdBy = nullableInt(rs, "created_by_user_id");
        Integer updatedBy = nullableInt(rs, "updated_by_user_id");
        return new SubscriptionPlan(
                rs.getInt("plan_id"),
                rs.getString("plan_code"),
                rs.getString("plan_name"),
                rs.getString("description"),
                rs.getDouble("price"),
                rs.getInt("duration_days"),
                rs.getInt("grace_days"),
                rs.getDouble("default_discount_percent"),
                rs.getDouble("max_discount_percent"),
                rs.getDouble("minimum_margin_percent"),
                SubscriptionPlanStatus.fromString(rs.getString("status")),
                rs.getBoolean("auto_renew"),
                rs.getBoolean("requires_approval"),
                createdBy,
                updatedBy,
                rs.getString("created_at"),
                rs.getString("updated_at"));
    }

    private CustomerSubscription mapEnrollment(ResultSet rs) throws SQLException {
        return new CustomerSubscription(
                rs.getInt("enrollment_id"),
                rs.getInt("customer_id"),
                rs.getInt("plan_id"),
                SubscriptionEnrollmentStatus.fromString(rs.getString("status")),
                rs.getString("start_date"),
                rs.getString("end_date"),
                rs.getString("grace_end_date"),
                rs.getString("enrollment_channel"),
                nullableInt(rs, "enrolled_by_user_id"),
                nullableInt(rs, "approved_by_user_id"),
                rs.getString("approval_reference"),
                rs.getString("cancellation_reason"),
                rs.getString("frozen_reason"),
                rs.getString("created_at"),
                rs.getString("updated_at"));
    }

    private SubscriptionApproval mapApproval(ResultSet rs) throws SQLException {
        return new SubscriptionApproval(
                rs.getInt("approval_id"),
                rs.getString("approval_type"),
                rs.getString("request_ref_type"),
                rs.getInt("request_ref_id"),
                rs.getInt("requested_by_user_id"),
                nullableInt(rs, "approver_user_id"),
                SubscriptionApprovalStatus.fromString(rs.getString("approval_status")),
                rs.getString("reason"),
                rs.getString("approved_at"),
                rs.getString("created_at"));
    }

    private SubscriptionDiscountOverride mapDiscountOverride(ResultSet rs) throws SQLException {
        return new SubscriptionDiscountOverride(
                rs.getInt("override_id"),
                nullableInt(rs, "bill_id"),
                nullableInt(rs, "bill_item_id"),
                nullableInt(rs, "customer_id"),
                nullableInt(rs, "enrollment_id"),
                rs.getDouble("requested_discount_percent"),
                nullableDouble(rs, "approved_discount_percent"),
                SubscriptionDiscountOverrideStatus.fromString(rs.getString("status")),
                rs.getString("reason"),
                rs.getInt("requested_by_user_id"),
                nullableInt(rs, "approved_by_user_id"),
                nullableInt(rs, "approval_id"),
                rs.getString("created_at"),
                rs.getString("approved_at"));
    }

    private SubscriptionPlanMedicineRule mapPlanMedicineRule(ResultSet rs) throws SQLException {
        return new SubscriptionPlanMedicineRule(
                rs.getInt("rule_id"),
                rs.getInt("plan_id"),
                rs.getInt("medicine_id"),
                rs.getString("medicine_name"),
                rs.getBoolean("include_rule"),
                rs.getDouble("discount_percent"),
                nullableDouble(rs, "max_discount_amount"),
                nullableDouble(rs, "min_margin_percent"),
                rs.getBoolean("active"),
                rs.getString("created_at"),
                rs.getString("updated_at"));
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private SubscriptionPlanStatus normalizePlanStatus(SubscriptionPlanStatus status) {
        return status == null ? SubscriptionPlanStatus.DRAFT : status;
    }

    private SubscriptionEnrollmentStatus normalizeEnrollmentStatus(SubscriptionEnrollmentStatus status) {
        return status == null ? SubscriptionEnrollmentStatus.ACTIVE : status;
    }

    public record ApplicableSubscription(
            int enrollmentId,
            int planId,
            String planCode,
            String planName,
            double defaultDiscountPercent,
            double maxDiscountPercent,
            double minimumMarginPercent,
            String approvalReference) {
    }

    public record EligibilityContext(
            CustomerSubscription enrollment,
            SubscriptionPlanStatus planStatus,
            String planName) {
    }
}
