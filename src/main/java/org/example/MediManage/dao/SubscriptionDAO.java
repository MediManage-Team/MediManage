package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.util.ReportingWindowUtils;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

public class SubscriptionDAO {
    private static final DateTimeFormatter DB_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_RECOMMENDATION_LOOKBACK_DAYS = 180;
    private static final int MIN_RECOMMENDATION_LOOKBACK_DAYS = 30;
    private static final int MAX_RECOMMENDATION_LOOKBACK_DAYS = 365;
    private static final int DEFAULT_RENEWAL_WINDOW_DAYS = 21;
    private static final int MIN_RENEWAL_WINDOW_DAYS = 1;
    private static final int MAX_RENEWAL_WINDOW_DAYS = 90;

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

    public List<OverrideFrequencySnapshot> getOverrideFrequencySnapshots(String sinceTimestamp, int minRequests) {
        List<OverrideFrequencySnapshot> rows = new ArrayList<>();
        if (sinceTimestamp == null || sinceTimestamp.trim().isEmpty()) {
            return rows;
        }

        int safeMinRequests = Math.max(1, minRequests);
        String sql = "SELECT requested_by_user_id, " +
                "COUNT(*) AS total_requests, " +
                "SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count, " +
                "SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count, " +
                "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count, " +
                "MIN(created_at) AS first_request_at, " +
                "MAX(created_at) AS latest_request_at " +
                "FROM subscription_discount_overrides " +
                "WHERE created_at >= ? " +
                "GROUP BY requested_by_user_id " +
                "HAVING COUNT(*) >= ? " +
                "ORDER BY total_requests DESC, latest_request_at DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sinceTimestamp.trim());
            ps.setInt(2, safeMinRequests);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new OverrideFrequencySnapshot(
                            rs.getInt("requested_by_user_id"),
                            rs.getInt("total_requests"),
                            rs.getInt("approved_count"),
                            rs.getInt("rejected_count"),
                            rs.getInt("pending_count"),
                            rs.getString("first_request_at"),
                            rs.getString("latest_request_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to list override frequency snapshots: " + e.getMessage());
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

    public List<CustomerPurchaseEvent> getCustomerPurchaseEvents(int customerId, int lookbackDays) {
        return getCustomerPurchaseEvents(customerId, lookbackDays, LocalDate.now());
    }

    public List<CustomerPurchaseEvent> getCustomerPurchaseEvents(int customerId, int lookbackDays, LocalDate referenceDate) {
        List<CustomerPurchaseEvent> events = new ArrayList<>();
        if (customerId <= 0) {
            return events;
        }

        int safeLookbackDays = normalizeRecommendationLookbackDays(lookbackDays);
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String fromTimestamp = safeReferenceDate.plusDays(1L)
                .atStartOfDay()
                .minusDays(safeLookbackDays)
                .format(DB_TIMESTAMP);
        String toTimestampExclusive = safeReferenceDate.plusDays(1L).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT bill_date, COALESCE(total_amount, 0) AS total_amount " +
                "FROM bills " +
                "WHERE customer_id = ? AND bill_date >= ? AND bill_date < ? " +
                "ORDER BY bill_date ASC, bill_id ASC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setString(2, fromTimestamp);
            ps.setString(3, toTimestampExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new CustomerPurchaseEvent(
                            rs.getString("bill_date"),
                            rs.getDouble("total_amount")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load customer purchase events for recommendations: " + e.getMessage());
        }
        return events;
    }

    public List<CustomerRefillEvent> getCustomerRefillEvents(int customerId, int lookbackDays) {
        return getCustomerRefillEvents(customerId, lookbackDays, LocalDate.now());
    }

    public List<CustomerRefillEvent> getCustomerRefillEvents(int customerId, int lookbackDays, LocalDate referenceDate) {
        List<CustomerRefillEvent> events = new ArrayList<>();
        if (customerId <= 0) {
            return events;
        }

        int safeLookbackDays = normalizeRecommendationLookbackDays(lookbackDays);
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String fromTimestamp = safeReferenceDate.plusDays(1L)
                .atStartOfDay()
                .minusDays(safeLookbackDays)
                .format(DB_TIMESTAMP);
        String toTimestampExclusive = safeReferenceDate.plusDays(1L).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT b.bill_date, bi.medicine_id, COALESCE(bi.quantity, 0) AS quantity, " +
                "COALESCE(bi.total, 0) AS line_total " +
                "FROM bills b " +
                "JOIN bill_items bi ON bi.bill_id = b.bill_id " +
                "WHERE b.customer_id = ? AND b.bill_date >= ? AND b.bill_date < ? " +
                "ORDER BY b.bill_date ASC, bi.item_id ASC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setString(2, fromTimestamp);
            ps.setString(3, toTimestampExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new CustomerRefillEvent(
                            rs.getInt("medicine_id"),
                            rs.getString("bill_date"),
                            rs.getInt("quantity"),
                            rs.getDouble("line_total")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load customer refill events for recommendations: " + e.getMessage());
        }
        return events;
    }

    public List<RenewalDueCandidate> getRenewalDueCandidates(int renewalWindowDays) {
        List<RenewalDueCandidate> rows = new ArrayList<>();
        int safeRenewalWindowDays = normalizeRenewalWindowDays(renewalWindowDays);
        String now = LocalDateTime.now().format(DB_TIMESTAMP);
        String renewalCutoff = LocalDateTime.now().plusDays(safeRenewalWindowDays).format(DB_TIMESTAMP);
        String sql = "SELECT cs.enrollment_id, cs.customer_id, cs.plan_id, cs.start_date, cs.end_date, " +
                "sp.plan_code, sp.plan_name, COALESCE(sp.price, 0) AS plan_price, " +
                "COALESCE(sp.duration_days, 30) AS duration_days " +
                "FROM customer_subscriptions cs " +
                "JOIN subscription_plans sp ON sp.plan_id = cs.plan_id " +
                "WHERE cs.status = 'ACTIVE' " +
                "AND sp.status = 'ACTIVE' " +
                "AND cs.end_date >= ? " +
                "AND cs.end_date <= ? " +
                "ORDER BY cs.end_date ASC, cs.enrollment_id ASC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now);
            ps.setString(2, renewalCutoff);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RenewalDueCandidate(
                            rs.getInt("enrollment_id"),
                            rs.getInt("customer_id"),
                            rs.getInt("plan_id"),
                            rs.getString("plan_code"),
                            rs.getString("plan_name"),
                            rs.getString("start_date"),
                            rs.getString("end_date"),
                            rs.getDouble("plan_price"),
                            Math.max(1, rs.getInt("duration_days"))));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load renewal-due candidates for propensity scoring: " + e.getMessage());
        }
        return rows;
    }

    public int getEnrollmentRenewalCount(int enrollmentId) {
        if (enrollmentId <= 0) {
            return 0;
        }
        String sql = "SELECT COUNT(*) AS cnt FROM customer_subscription_events " +
                "WHERE enrollment_id = ? AND event_type = 'RENEW'";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to read enrollment renewal count for propensity scoring: " + e.getMessage());
        }
        return 0;
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

    public Map<String, SubscriptionDiscountEngine.DiscountRule> loadCategoryRules(int planId) {
        Map<String, SubscriptionDiscountEngine.DiscountRule> rules = new LinkedHashMap<>();
        String sql = "SELECT category_name, include_rule, discount_percent, max_discount_amount, min_margin_percent, active " +
                "FROM subscription_plan_category_rules " +
                "WHERE plan_id = ? " +
                "ORDER BY updated_at DESC, rule_id DESC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String categoryName = rs.getString("category_name");
                    if (categoryName == null || categoryName.isBlank()) {
                        continue;
                    }
                    String normalizedCategory = categoryName.trim().toLowerCase(Locale.ROOT);
                    if (rules.containsKey(normalizedCategory)) {
                        continue;
                    }
                    boolean includeRule = rs.getBoolean("include_rule");
                    Double discountPercent = nullableDouble(rs, "discount_percent");
                    Double maxDiscountAmount = nullableDouble(rs, "max_discount_amount");
                    Double minMarginPercent = nullableDouble(rs, "min_margin_percent");
                    boolean active = rs.getBoolean("active");
                    rules.put(normalizedCategory, new SubscriptionDiscountEngine.DiscountRule(
                            includeRule,
                            discountPercent,
                            maxDiscountAmount,
                            minMarginPercent,
                            active));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load subscription category rules for plan " + planId + ": " + e.getMessage());
        }
        return rules;
    }

    public Map<Integer, String> loadMedicineCategoryById(List<Integer> medicineIds) {
        if (medicineIds == null || medicineIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Integer> distinctIds = new HashSet<>();
        for (Integer medicineId : medicineIds) {
            if (medicineId != null && medicineId > 0) {
                distinctIds.add(medicineId);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        StringJoiner placeholders = new StringJoiner(", ");
        for (int ignored : distinctIds) {
            placeholders.add("?");
        }

        String sql = "SELECT medicine_id, generic_name FROM medicines WHERE medicine_id IN (" + placeholders + ")";
        Map<Integer, String> categories = new HashMap<>();
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Integer medicineId : distinctIds) {
                ps.setInt(index++, medicineId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int medicineId = rs.getInt("medicine_id");
                    String genericName = rs.getString("generic_name");
                    if (genericName == null || genericName.isBlank()) {
                        continue;
                    }
                    categories.put(medicineId, genericName.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load medicine categories for subscription rules: " + e.getMessage());
        }
        return categories;
    }

    public SubscriptionDashboardSnapshot getDashboardSnapshot(int renewalWindowDays) {
        int safeRenewalWindowDays = Math.max(1, renewalWindowDays);
        LocalDateTime now = LocalDateTime.now();
        String nowTs = now.format(DB_TIMESTAMP);
        String renewalDueCutoff = now.plusDays(safeRenewalWindowDays).format(DB_TIMESTAMP);
        String dayStart = now.toLocalDate().atStartOfDay().format(DB_TIMESTAMP);
        String dayEndExclusive = now.toLocalDate().plusDays(1).atStartOfDay().format(DB_TIMESTAMP);

        String activeSql = "SELECT COUNT(*) AS cnt " +
                "FROM customer_subscriptions cs " +
                "JOIN subscription_plans sp ON sp.plan_id = cs.plan_id " +
                "WHERE cs.status = 'ACTIVE' " +
                "AND sp.status = 'ACTIVE' " +
                "AND cs.start_date <= ? " +
                "AND (cs.end_date >= ? OR (cs.grace_end_date IS NOT NULL AND cs.grace_end_date >= ?))";

        String renewalsSql = "SELECT COUNT(*) AS cnt " +
                "FROM customer_subscriptions cs " +
                "JOIN subscription_plans sp ON sp.plan_id = cs.plan_id " +
                "WHERE cs.status = 'ACTIVE' " +
                "AND sp.status = 'ACTIVE' " +
                "AND cs.end_date >= ? " +
                "AND cs.end_date <= ?";

        String savingsSql = "SELECT COALESCE(SUM(subscription_savings_amount), 0) AS total " +
                "FROM bills WHERE bill_date >= ? AND bill_date < ?";

        String pendingOverrideSql = "SELECT COUNT(*) AS cnt " +
                "FROM subscription_discount_overrides WHERE status = 'PENDING'";

        try (Connection conn = DatabaseUtil.getConnection()) {
            long activeSubscribers = queryLong(
                    conn,
                    activeSql,
                    ps -> {
                        ps.setString(1, nowTs);
                        ps.setString(2, nowTs);
                        ps.setString(3, nowTs);
                    });
            long renewalsDueSoon = queryLong(
                    conn,
                    renewalsSql,
                    ps -> {
                        ps.setString(1, nowTs);
                        ps.setString(2, renewalDueCutoff);
                    });
            double dailySubscriptionSavings = queryDouble(
                    conn,
                    savingsSql,
                    ps -> {
                        ps.setString(1, dayStart);
                        ps.setString(2, dayEndExclusive);
                    });
            long pendingOverrideCount = queryLong(conn, pendingOverrideSql, null);

            return new SubscriptionDashboardSnapshot(
                    activeSubscribers,
                    renewalsDueSoon,
                    dailySubscriptionSavings,
                    pendingOverrideCount);
        } catch (Exception e) {
            System.err.println("Failed to load subscription dashboard snapshot: " + e.getMessage());
            return new SubscriptionDashboardSnapshot(0L, 0L, 0.0, 0L);
        }
    }

    public Optional<WeeklyAnalyticsSummaryRow> refreshWeeklyAnalyticsSummary(LocalDate referenceDate, String timezoneName) {
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String safeTimezoneName = normalizeTimezoneName(timezoneName);
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(safeReferenceDate);
        LocalDate weekStart = weeklyWindow.startDate();
        LocalDate weekEnd = weeklyWindow.endDate();
        String weekStartDate = weekStart.toString();
        String weekEndDate = weekEnd.toString();
        String rangeStart = weekStart.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = weekEnd.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String weekEndSnapshot = weekEnd.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String renewalsDueCutoff = weekEnd.plusDays(8).atStartOfDay().format(DB_TIMESTAMP);

        String totalBillsSql = "SELECT COUNT(*) AS cnt FROM bills WHERE bill_date >= ? AND bill_date < ?";
        String subscriptionTotalsSql = "SELECT " +
                "COUNT(*) AS bill_count, " +
                "COALESCE(SUM(COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)), 0) AS gross_amount, " +
                "COALESCE(SUM(COALESCE(b.total_amount, 0)), 0) AS net_amount, " +
                "COALESCE(SUM(COALESCE(b.subscription_savings_amount, 0)), 0) AS savings_amount " +
                "FROM bills b " +
                "WHERE b.bill_date >= ? " +
                "AND b.bill_date < ? " +
                "AND b.subscription_plan_id IS NOT NULL";
        String activeSubscribersSql = "SELECT COUNT(*) AS cnt " +
                "FROM customer_subscriptions cs " +
                "JOIN subscription_plans sp ON sp.plan_id = cs.plan_id " +
                "WHERE cs.status = 'ACTIVE' " +
                "AND sp.status = 'ACTIVE' " +
                "AND cs.start_date < ? " +
                "AND (cs.end_date >= ? OR (cs.grace_end_date IS NOT NULL AND cs.grace_end_date >= ?))";
        String renewalsDueSql = "SELECT COUNT(*) AS cnt " +
                "FROM customer_subscriptions cs " +
                "JOIN subscription_plans sp ON sp.plan_id = cs.plan_id " +
                "WHERE cs.status = 'ACTIVE' " +
                "AND sp.status = 'ACTIVE' " +
                "AND cs.end_date >= ? " +
                "AND cs.end_date < ?";
        String pendingOverrideSql = "SELECT COUNT(*) AS cnt " +
                "FROM subscription_discount_overrides " +
                "WHERE status = 'PENDING' AND created_at < ?";
        String highPricingAlertsSql = "SELECT COUNT(*) AS cnt FROM (" +
                "SELECT CASE " +
                "WHEN COALESCE(b.subscription_savings_amount, 0) < 0 THEN 'NEGATIVE_SAVINGS' " +
                "WHEN COALESCE(b.subscription_discount_percent, 0) < 0 OR COALESCE(b.subscription_discount_percent, 0) > 100 THEN 'DISCOUNT_PERCENT_OUT_OF_RANGE' " +
                "WHEN (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) < 0 THEN 'NEGATIVE_GROSS_BEFORE_DISCOUNT' " +
                "WHEN COALESCE(b.subscription_savings_amount, 0) > (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) + 0.01 THEN 'SAVINGS_EXCEED_GROSS' " +
                "WHEN (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) > 0 " +
                "AND ABS(((COALESCE(b.subscription_savings_amount, 0) / (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0))) * 100.0) - COALESCE(b.subscription_discount_percent, 0)) > 1.0 THEN 'DISCOUNT_PERCENT_MISMATCH' " +
                "ELSE 'NONE' END AS alert_code " +
                "FROM bills b " +
                "WHERE b.bill_date >= ? " +
                "AND b.bill_date < ? " +
                "AND b.subscription_plan_id IS NOT NULL" +
                ") q " +
                "WHERE q.alert_code IN ('NEGATIVE_SAVINGS', 'DISCOUNT_PERCENT_OUT_OF_RANGE', 'NEGATIVE_GROSS_BEFORE_DISCOUNT', 'SAVINGS_EXCEED_GROSS')";
        String highOverrideSignalsSql = "SELECT COUNT(*) AS cnt FROM (" +
                "SELECT o.requested_by_user_id AS requested_by_user_id, " +
                "COUNT(*) AS total_requests, " +
                "CASE WHEN COUNT(*) = 0 THEN 0.0 ELSE (SUM(CASE WHEN o.status = 'REJECTED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) END AS rejection_rate_percent, " +
                "COALESCE(AVG(o.requested_discount_percent), 0) AS average_requested_percent, " +
                "COALESCE(MAX(o.requested_discount_percent), 0) AS max_requested_percent " +
                "FROM subscription_discount_overrides o " +
                "WHERE o.created_at >= ? " +
                "AND o.created_at < ? " +
                "GROUP BY o.requested_by_user_id" +
                ") q " +
                "WHERE q.total_requests >= 5 " +
                "AND (q.rejection_rate_percent >= 60.0 OR q.average_requested_percent >= 25.0 OR q.max_requested_percent >= 35.0)";
        String openHighCriticalFeedbackSql = "SELECT COUNT(*) AS cnt " +
                "FROM subscription_pilot_feedback " +
                "WHERE reported_at < ? " +
                "AND status <> 'RESOLVED' " +
                "AND severity IN ('HIGH', 'CRITICAL')";

        List<PlanRevenueImpactRow> planRows = getPlanRevenueImpact(weekStart, weekEnd);
        try (Connection conn = DatabaseUtil.getConnection()) {
            long totalBillCount = queryLong(
                    conn,
                    totalBillsSql,
                    ps -> {
                        ps.setString(1, rangeStart);
                        ps.setString(2, rangeEndExclusive);
                    });
            long subscriptionBillCount = queryLong(
                    conn,
                    subscriptionTotalsSql,
                    ps -> {
                        ps.setString(1, rangeStart);
                        ps.setString(2, rangeEndExclusive);
                    });
            double grossAmountBeforeDiscount = queryDouble(
                    conn,
                    "SELECT COALESCE(SUM(COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)), 0) " +
                            "FROM bills b " +
                            "WHERE b.bill_date >= ? " +
                            "AND b.bill_date < ? " +
                            "AND b.subscription_plan_id IS NOT NULL",
                    ps -> {
                        ps.setString(1, rangeStart);
                        ps.setString(2, rangeEndExclusive);
                    });
            double netBilledAmount = queryDouble(
                    conn,
                    "SELECT COALESCE(SUM(COALESCE(b.total_amount, 0)), 0) " +
                            "FROM bills b " +
                            "WHERE b.bill_date >= ? " +
                            "AND b.bill_date < ? " +
                            "AND b.subscription_plan_id IS NOT NULL",
                    ps -> {
                        ps.setString(1, rangeStart);
                        ps.setString(2, rangeEndExclusive);
                    });
            double totalSavings = queryDouble(
                    conn,
                    "SELECT COALESCE(SUM(COALESCE(b.subscription_savings_amount, 0)), 0) " +
                            "FROM bills b " +
                            "WHERE b.bill_date >= ? " +
                            "AND b.bill_date < ? " +
                            "AND b.subscription_plan_id IS NOT NULL",
                    ps -> {
                        ps.setString(1, rangeStart);
                        ps.setString(2, rangeEndExclusive);
                    });
            long activeSubscribersSnapshot = queryLong(
                    conn,
                    activeSubscribersSql,
                    ps -> {
                        ps.setString(1, weekEndSnapshot);
                        ps.setString(2, weekEndSnapshot);
                        ps.setString(3, weekEndSnapshot);
                    });
            long renewalsDueNext7Days = queryLong(
                    conn,
                    renewalsDueSql,
                    ps -> {
                        ps.setString(1, weekEndSnapshot);
                        ps.setString(2, renewalsDueCutoff);
                    });
            long pendingOverrideCount = queryLong(
                    conn,
                    pendingOverrideSql,
                    ps -> ps.setString(1, weekEndSnapshot));
            long highPricingAlertCount = queryLong(
                    conn,
                    highPricingAlertsSql,
                    ps -> {
                        ps.setString(1, rangeStart);
                        ps.setString(2, rangeEndExclusive);
                    });
            long highOverrideAbuseSignalCount = queryLong(
                    conn,
                    highOverrideSignalsSql,
                    ps -> {
                        ps.setString(1, rangeStart);
                        ps.setString(2, rangeEndExclusive);
                    });
            long openHighCriticalFeedbackCount = queryLong(
                    conn,
                    openHighCriticalFeedbackSql,
                    ps -> ps.setString(1, weekEndSnapshot));

            grossAmountBeforeDiscount = round2(grossAmountBeforeDiscount);
            netBilledAmount = round2(netBilledAmount);
            totalSavings = round2(totalSavings);
            double leakagePercent = grossAmountBeforeDiscount <= 0.0
                    ? 0.0
                    : round4((totalSavings / grossAmountBeforeDiscount) * 100.0);

            upsertWeeklyAnalyticsSummary(
                    conn,
                    weekStartDate,
                    weekEndDate,
                    safeTimezoneName,
                    totalBillCount,
                    subscriptionBillCount,
                    grossAmountBeforeDiscount,
                    netBilledAmount,
                    totalSavings,
                    leakagePercent,
                    activeSubscribersSnapshot,
                    renewalsDueNext7Days,
                    pendingOverrideCount,
                    highPricingAlertCount,
                    highOverrideAbuseSignalCount,
                    openHighCriticalFeedbackCount);
            replaceWeeklyPlanRevenueSummary(
                    conn,
                    weekStartDate,
                    weekEndDate,
                    safeTimezoneName,
                    planRows);

            return getWeeklyAnalyticsSummary(weekStart, safeTimezoneName);
        } catch (Exception e) {
            System.err.println("Failed to refresh weekly analytics summary for " + weekStartDate + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<WeeklyAnalyticsSummaryRow> getWeeklyAnalyticsSummary(LocalDate referenceDate, String timezoneName) {
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String safeTimezoneName = normalizeTimezoneName(timezoneName);
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(safeReferenceDate);
        String weekStartDate = weeklyWindow.startDate().toString();

        String sql = "SELECT week_start_date, week_end_date, timezone_name, " +
                "total_bill_count, subscription_bill_count, gross_amount_before_discount, net_billed_amount, " +
                "total_savings, leakage_percent, active_subscribers_snapshot, renewals_due_next_7_days, " +
                "pending_override_count, high_pricing_alert_count, high_override_abuse_signal_count, " +
                "open_high_critical_feedback_count, generated_at " +
                "FROM subscription_weekly_summary " +
                "WHERE week_start_date = ? AND timezone_name = ? " +
                "LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, weekStartDate);
            ps.setString(2, safeTimezoneName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new WeeklyAnalyticsSummaryRow(
                            rs.getString("week_start_date"),
                            rs.getString("week_end_date"),
                            rs.getString("timezone_name"),
                            rs.getLong("total_bill_count"),
                            rs.getLong("subscription_bill_count"),
                            rs.getDouble("gross_amount_before_discount"),
                            rs.getDouble("net_billed_amount"),
                            rs.getDouble("total_savings"),
                            rs.getDouble("leakage_percent"),
                            rs.getLong("active_subscribers_snapshot"),
                            rs.getLong("renewals_due_next_7_days"),
                            rs.getLong("pending_override_count"),
                            rs.getLong("high_pricing_alert_count"),
                            rs.getLong("high_override_abuse_signal_count"),
                            rs.getLong("open_high_critical_feedback_count"),
                            rs.getString("generated_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch weekly analytics summary for " + weekStartDate + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<WeeklyLeakageHistoryRow> getRecentWeeklyLeakageHistory(
            LocalDate referenceDate,
            String timezoneName,
            int weekLimit) {
        List<WeeklyLeakageHistoryRow> rows = new ArrayList<>();
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String safeTimezoneName = normalizeTimezoneName(timezoneName);
        int safeWeekLimit = weekLimit <= 0 ? 8 : Math.min(weekLimit, 52);
        ReportingWindowUtils.WeeklyWindow currentWeek = ReportingWindowUtils.mondayToSundayWindow(safeReferenceDate);
        String currentWeekStartDate = currentWeek.startDate().toString();

        String sql = "SELECT week_start_date, week_end_date, timezone_name, leakage_percent, " +
                "total_savings, gross_amount_before_discount, generated_at " +
                "FROM subscription_weekly_summary " +
                "WHERE timezone_name = ? AND week_start_date < ? " +
                "ORDER BY week_start_date DESC " +
                "LIMIT ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeTimezoneName);
            ps.setString(2, currentWeekStartDate);
            ps.setInt(3, safeWeekLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new WeeklyLeakageHistoryRow(
                            rs.getString("week_start_date"),
                            rs.getString("week_end_date"),
                            rs.getString("timezone_name"),
                            rs.getDouble("leakage_percent"),
                            rs.getDouble("total_savings"),
                            rs.getDouble("gross_amount_before_discount"),
                            rs.getString("generated_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch weekly leakage history before " + currentWeekStartDate + ": " + e.getMessage());
        }
        return rows;
    }

    public List<WeeklyPlanRevenueSummaryRow> getWeeklyPlanRevenueSummary(LocalDate referenceDate, String timezoneName) {
        List<WeeklyPlanRevenueSummaryRow> rows = new ArrayList<>();
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String safeTimezoneName = normalizeTimezoneName(timezoneName);
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(safeReferenceDate);
        String weekStartDate = weeklyWindow.startDate().toString();

        String sql = "SELECT week_start_date, week_end_date, timezone_name, plan_id, plan_code, plan_name, " +
                "bill_count, gross_amount_before_discount, net_billed_amount, total_savings, average_savings_per_bill, leakage_percent, generated_at " +
                "FROM subscription_weekly_plan_summary " +
                "WHERE week_start_date = ? AND timezone_name = ? " +
                "ORDER BY total_savings DESC, bill_count DESC, plan_id ASC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, weekStartDate);
            ps.setString(2, safeTimezoneName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new WeeklyPlanRevenueSummaryRow(
                            rs.getString("week_start_date"),
                            rs.getString("week_end_date"),
                            rs.getString("timezone_name"),
                            rs.getInt("plan_id"),
                            rs.getString("plan_code"),
                            rs.getString("plan_name"),
                            rs.getLong("bill_count"),
                            rs.getDouble("gross_amount_before_discount"),
                            rs.getDouble("net_billed_amount"),
                            rs.getDouble("total_savings"),
                            rs.getDouble("average_savings_per_bill"),
                            rs.getDouble("leakage_percent"),
                            rs.getString("generated_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch weekly plan revenue summary for " + weekStartDate + ": " + e.getMessage());
        }
        return rows;
    }

    public List<PlanRevenueImpactRow> getPlanRevenueImpact(LocalDate start, LocalDate end) {
        List<PlanRevenueImpactRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT b.subscription_plan_id AS plan_id, " +
                "COALESCE(sp.plan_code, 'PLAN-' || b.subscription_plan_id) AS plan_code, " +
                "COALESCE(sp.plan_name, 'Unknown Plan') AS plan_name, " +
                "COUNT(*) AS bill_count, " +
                "COALESCE(SUM(b.total_amount), 0) AS net_amount, " +
                "COALESCE(SUM(b.subscription_savings_amount), 0) AS savings_amount " +
                "FROM bills b " +
                "LEFT JOIN subscription_plans sp ON sp.plan_id = b.subscription_plan_id " +
                "WHERE b.bill_date >= ? " +
                "AND b.bill_date < ? " +
                "AND b.subscription_plan_id IS NOT NULL " +
                "GROUP BY b.subscription_plan_id, sp.plan_code, sp.plan_name " +
                "ORDER BY (COALESCE(SUM(b.total_amount), 0) + COALESCE(SUM(b.subscription_savings_amount), 0)) DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int planId = rs.getInt("plan_id");
                    long billCount = rs.getLong("bill_count");
                    double netAmount = round2(rs.getDouble("net_amount"));
                    double savings = round2(rs.getDouble("savings_amount"));
                    double grossBeforeDiscount = round2(netAmount + savings);
                    double averageSavings = billCount <= 0 ? 0.0 : round2(savings / billCount);
                    double leakagePercent = grossBeforeDiscount <= 0.0
                            ? 0.0
                            : round4((savings / grossBeforeDiscount) * 100.0);

                    rows.add(new PlanRevenueImpactRow(
                            planId,
                            rs.getString("plan_code"),
                            rs.getString("plan_name"),
                            billCount,
                            grossBeforeDiscount,
                            netAmount,
                            savings,
                            averageSavings,
                            leakagePercent));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch plan revenue impact report: " + e.getMessage());
        }
        return rows;
    }

    public List<DiscountLeakageRow> getDiscountLeakageByDay(LocalDate start, LocalDate end) {
        List<DiscountLeakageRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT DATE(b.bill_date) AS bill_day, " +
                "COUNT(*) AS bill_count, " +
                "COALESCE(SUM(b.total_amount), 0) AS net_amount, " +
                "COALESCE(SUM(b.subscription_savings_amount), 0) AS savings_amount " +
                "FROM bills b " +
                "WHERE b.bill_date >= ? " +
                "AND b.bill_date < ? " +
                "AND b.subscription_plan_id IS NOT NULL " +
                "GROUP BY DATE(b.bill_date) " +
                "ORDER BY bill_day ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long billCount = rs.getLong("bill_count");
                    double netAmount = round2(rs.getDouble("net_amount"));
                    double savings = round2(rs.getDouble("savings_amount"));
                    double grossBeforeDiscount = round2(netAmount + savings);
                    double leakagePercent = grossBeforeDiscount <= 0.0
                            ? 0.0
                            : round4((savings / grossBeforeDiscount) * 100.0);

                    rows.add(new DiscountLeakageRow(
                            rs.getString("bill_day"),
                            billCount,
                            grossBeforeDiscount,
                            netAmount,
                            savings,
                            leakagePercent));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch discount leakage report: " + e.getMessage());
        }
        return rows;
    }

    public List<RejectedOverrideReportRow> getRejectedOverrideAttempts(LocalDate start, LocalDate end, int limit) {
        List<RejectedOverrideReportRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        int safeLimit = Math.max(1, limit);
        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT o.override_id, o.customer_id, o.enrollment_id, " +
                "o.requested_discount_percent, o.reason AS request_reason, " +
                "o.requested_by_user_id, req.username AS requested_by_username, " +
                "o.approved_by_user_id AS rejected_by_user_id, rej.username AS rejected_by_username, " +
                "o.created_at AS requested_at, o.approved_at AS rejected_at " +
                "FROM subscription_discount_overrides o " +
                "LEFT JOIN users req ON req.user_id = o.requested_by_user_id " +
                "LEFT JOIN users rej ON rej.user_id = o.approved_by_user_id " +
                "WHERE o.status = 'REJECTED' " +
                "AND COALESCE(o.approved_at, o.created_at) >= ? " +
                "AND COALESCE(o.approved_at, o.created_at) < ? " +
                "ORDER BY COALESCE(o.approved_at, o.created_at) DESC, o.override_id DESC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            ps.setInt(3, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RejectedOverrideReportRow(
                            rs.getInt("override_id"),
                            nullableInt(rs, "customer_id"),
                            nullableInt(rs, "enrollment_id"),
                            round4(rs.getDouble("requested_discount_percent")),
                            rs.getString("request_reason"),
                            rs.getInt("requested_by_user_id"),
                            rs.getString("requested_by_username"),
                            nullableInt(rs, "rejected_by_user_id"),
                            rs.getString("rejected_by_username"),
                            rs.getString("requested_at"),
                            rs.getString("rejected_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch rejected override attempts report: " + e.getMessage());
        }
        return rows;
    }

    public List<PricingIntegrityAlertRow> getPricingIntegrityAlerts(LocalDate start, LocalDate end, int limit) {
        List<PricingIntegrityAlertRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        int safeLimit = Math.max(1, limit);
        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);

        String sql = "SELECT q.bill_id, q.bill_date, q.plan_id, q.plan_code, q.plan_name, " +
                "q.net_amount, q.savings_amount, q.configured_discount_percent, q.gross_before_discount, q.alert_code " +
                "FROM (" +
                "SELECT b.bill_id AS bill_id, b.bill_date AS bill_date, " +
                "b.subscription_plan_id AS plan_id, " +
                "COALESCE(sp.plan_code, 'PLAN-' || b.subscription_plan_id) AS plan_code, " +
                "COALESCE(sp.plan_name, 'Unknown Plan') AS plan_name, " +
                "COALESCE(b.total_amount, 0) AS net_amount, " +
                "COALESCE(b.subscription_savings_amount, 0) AS savings_amount, " +
                "COALESCE(b.subscription_discount_percent, 0) AS configured_discount_percent, " +
                "(COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) AS gross_before_discount, " +
                "CASE " +
                "WHEN COALESCE(b.subscription_savings_amount, 0) < 0 THEN 'NEGATIVE_SAVINGS' " +
                "WHEN COALESCE(b.subscription_discount_percent, 0) < 0 OR COALESCE(b.subscription_discount_percent, 0) > 100 THEN 'DISCOUNT_PERCENT_OUT_OF_RANGE' " +
                "WHEN (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) < 0 THEN 'NEGATIVE_GROSS_BEFORE_DISCOUNT' " +
                "WHEN COALESCE(b.subscription_savings_amount, 0) > (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) + 0.01 THEN 'SAVINGS_EXCEED_GROSS' " +
                "WHEN (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) > 0 " +
                "AND ABS(((COALESCE(b.subscription_savings_amount, 0) / (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0))) * 100.0) - COALESCE(b.subscription_discount_percent, 0)) > 1.0 THEN 'DISCOUNT_PERCENT_MISMATCH' " +
                "ELSE 'NONE' END AS alert_code " +
                "FROM bills b " +
                "LEFT JOIN subscription_plans sp ON sp.plan_id = b.subscription_plan_id " +
                "WHERE b.bill_date >= ? " +
                "AND b.bill_date < ? " +
                "AND b.subscription_plan_id IS NOT NULL" +
                ") q " +
                "WHERE q.alert_code <> 'NONE' " +
                "ORDER BY q.bill_date DESC, q.bill_id DESC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            ps.setInt(3, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double netAmount = round2(rs.getDouble("net_amount"));
                    double savings = round2(rs.getDouble("savings_amount"));
                    double grossBeforeDiscount = round2(rs.getDouble("gross_before_discount"));
                    double configuredPercent = round4(rs.getDouble("configured_discount_percent"));
                    double computedPercent = grossBeforeDiscount <= 0.0
                            ? 0.0
                            : round4((savings / grossBeforeDiscount) * 100.0);
                    String alertCode = rs.getString("alert_code");
                    rows.add(new PricingIntegrityAlertRow(
                            rs.getInt("bill_id"),
                            rs.getString("bill_date"),
                            rs.getInt("plan_id"),
                            rs.getString("plan_code"),
                            rs.getString("plan_name"),
                            grossBeforeDiscount,
                            netAmount,
                            savings,
                            configuredPercent,
                            computedPercent,
                            alertCode,
                            classifyPricingAlertSeverity(alertCode)));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch pricing integrity alerts: " + e.getMessage());
        }
        return rows;
    }

    public List<OverrideAbuseSignalRow> getOverrideAbuseSignals(LocalDate start, LocalDate end, int minRequests) {
        List<OverrideAbuseSignalRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        int safeMinRequests = Math.max(1, minRequests);
        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT o.requested_by_user_id, req.username AS requested_by_username, " +
                "COUNT(*) AS total_requests, " +
                "SUM(CASE WHEN o.status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count, " +
                "SUM(CASE WHEN o.status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count, " +
                "SUM(CASE WHEN o.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count, " +
                "COALESCE(AVG(o.requested_discount_percent), 0) AS avg_requested_percent, " +
                "COALESCE(MAX(o.requested_discount_percent), 0) AS max_requested_percent, " +
                "MIN(o.created_at) AS first_request_at, " +
                "MAX(o.created_at) AS latest_request_at " +
                "FROM subscription_discount_overrides o " +
                "LEFT JOIN users req ON req.user_id = o.requested_by_user_id " +
                "WHERE o.created_at >= ? " +
                "AND o.created_at < ? " +
                "GROUP BY o.requested_by_user_id, req.username " +
                "HAVING COUNT(*) >= ? " +
                "ORDER BY COUNT(*) DESC, COALESCE(AVG(o.requested_discount_percent), 0) DESC, o.requested_by_user_id ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            ps.setInt(3, safeMinRequests);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int totalRequests = rs.getInt("total_requests");
                    int approvedCount = rs.getInt("approved_count");
                    int rejectedCount = rs.getInt("rejected_count");
                    int pendingCount = rs.getInt("pending_count");
                    double approvalRatePercent = totalRequests <= 0
                            ? 0.0
                            : round4((approvedCount * 100.0) / totalRequests);
                    double rejectionRatePercent = totalRequests <= 0
                            ? 0.0
                            : round4((rejectedCount * 100.0) / totalRequests);
                    double averageRequestedPercent = round4(rs.getDouble("avg_requested_percent"));
                    double maxRequestedPercent = round4(rs.getDouble("max_requested_percent"));
                    String severity = classifyOverrideAbuseSeverity(
                            totalRequests,
                            rejectionRatePercent,
                            averageRequestedPercent,
                            maxRequestedPercent);

                    rows.add(new OverrideAbuseSignalRow(
                            rs.getInt("requested_by_user_id"),
                            rs.getString("requested_by_username"),
                            totalRequests,
                            approvedCount,
                            rejectedCount,
                            pendingCount,
                            approvalRatePercent,
                            rejectionRatePercent,
                            averageRequestedPercent,
                            maxRequestedPercent,
                            rs.getString("first_request_at"),
                            rs.getString("latest_request_at"),
                            severity));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch override abuse signals: " + e.getMessage());
        }
        return rows;
    }

    public List<EnrollmentAbuseSignalRow> getEnrollmentAbuseSignals(LocalDate start, LocalDate end, int minEvents) {
        List<EnrollmentAbuseSignalRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        int safeMinEvents = Math.max(1, minEvents);
        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT cs.customer_id AS customer_id, " +
                "COUNT(*) AS total_events, " +
                "SUM(CASE WHEN e.event_type = 'PLAN_CHANGE' THEN 1 ELSE 0 END) AS plan_change_count, " +
                "SUM(CASE WHEN e.event_type = 'CANCEL' THEN 1 ELSE 0 END) AS cancellation_count, " +
                "SUM(CASE WHEN e.event_type = 'FREEZE' THEN 1 ELSE 0 END) AS freeze_count, " +
                "SUM(CASE WHEN e.event_type = 'ENROLL' AND cs.start_date < cs.created_at THEN 1 ELSE 0 END) AS backdated_enroll_count, " +
                "COUNT(DISTINCT COALESCE(e.new_plan_id, e.old_plan_id, cs.plan_id)) AS distinct_plan_count, " +
                "MIN(e.effective_at) AS first_event_at, " +
                "MAX(e.effective_at) AS latest_event_at " +
                "FROM customer_subscription_events e " +
                "JOIN customer_subscriptions cs ON cs.enrollment_id = e.enrollment_id " +
                "WHERE e.effective_at >= ? " +
                "AND e.effective_at < ? " +
                "GROUP BY cs.customer_id " +
                "HAVING COUNT(*) >= ? " +
                "ORDER BY COUNT(*) DESC, SUM(CASE WHEN e.event_type = 'PLAN_CHANGE' THEN 1 ELSE 0 END) DESC, cs.customer_id ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            ps.setInt(3, safeMinEvents);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new EnrollmentAbuseSignalRow(
                            rs.getInt("customer_id"),
                            rs.getInt("total_events"),
                            rs.getInt("plan_change_count"),
                            rs.getInt("cancellation_count"),
                            rs.getInt("freeze_count"),
                            rs.getInt("backdated_enroll_count"),
                            rs.getInt("distinct_plan_count"),
                            rs.getString("first_event_at"),
                            rs.getString("latest_event_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch enrollment abuse signals: " + e.getMessage());
        }
        return rows;
    }

    public int createPilotFeedback(
            String category,
            String severity,
            String title,
            String details,
            int reportedByUserId,
            Integer ownerUserId,
            Integer linkedBillId,
            Integer linkedOverrideId) throws SQLException {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) {
            throw new IllegalArgumentException("Pilot feedback title is required.");
        }

        String sql = "INSERT INTO subscription_pilot_feedback (" +
                "category, severity, status, title, details, reported_by_user_id, owner_user_id, linked_bill_id, linked_override_id" +
                ") VALUES (?, ?, 'OPEN', ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, normalizeFeedbackCategory(category));
            ps.setString(2, normalizeFeedbackSeverity(severity));
            ps.setString(3, safeTitle);
            ps.setString(4, normalizeOptionalText(details));
            ps.setInt(5, reportedByUserId);
            ps.setObject(6, ownerUserId);
            ps.setObject(7, linkedBillId);
            ps.setObject(8, linkedOverrideId);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create pilot feedback.");
    }

    public void updatePilotFeedbackStatus(
            int feedbackId,
            String status,
            Integer ownerUserId,
            String resolutionNotes) throws SQLException {
        String normalizedStatus = normalizeFeedbackStatus(status);
        String normalizedNotes = normalizeOptionalText(resolutionNotes);
        String resolvedAt = "RESOLVED".equals(normalizedStatus) ? LocalDateTime.now().format(DB_TIMESTAMP) : null;
        String sql = "UPDATE subscription_pilot_feedback SET " +
                "status = ?, owner_user_id = COALESCE(?, owner_user_id), resolution_notes = ?, " +
                "resolved_at = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE feedback_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedStatus);
            ps.setObject(2, ownerUserId);
            ps.setString(3, normalizedNotes);
            ps.setString(4, resolvedAt);
            ps.setInt(5, feedbackId);
            ps.executeUpdate();
        }
    }

    public List<PilotFeedbackRow> getPilotFeedback(LocalDate start, LocalDate end, String statusFilter, int limit) {
        List<PilotFeedbackRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        int safeLimit = Math.max(1, limit);
        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String normalizedStatusFilter = normalizeFeedbackStatusFilter(statusFilter);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT f.feedback_id, f.category, f.severity, f.status, f.title, f.details, ")
                .append("f.reported_by_user_id, reporter.username AS reported_by_username, ")
                .append("f.owner_user_id, owner.username AS owner_username, ")
                .append("f.linked_bill_id, f.linked_override_id, f.resolution_notes, ")
                .append("f.reported_at, f.updated_at, f.resolved_at ")
                .append("FROM subscription_pilot_feedback f ")
                .append("LEFT JOIN users reporter ON reporter.user_id = f.reported_by_user_id ")
                .append("LEFT JOIN users owner ON owner.user_id = f.owner_user_id ")
                .append("WHERE f.reported_at >= ? AND f.reported_at < ? ");
        if (!"ALL".equals(normalizedStatusFilter)) {
            sql.append("AND f.status = ? ");
        }
        sql.append("ORDER BY CASE f.status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END, ")
                .append("f.reported_at DESC, f.feedback_id DESC ")
                .append("LIMIT ?");

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            ps.setString(paramIndex++, rangeStart);
            ps.setString(paramIndex++, rangeEndExclusive);
            if (!"ALL".equals(normalizedStatusFilter)) {
                ps.setString(paramIndex++, normalizedStatusFilter);
            }
            ps.setInt(paramIndex, safeLimit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PilotFeedbackRow(
                            rs.getInt("feedback_id"),
                            rs.getString("category"),
                            rs.getString("severity"),
                            rs.getString("status"),
                            rs.getString("title"),
                            rs.getString("details"),
                            rs.getInt("reported_by_user_id"),
                            rs.getString("reported_by_username"),
                            nullableInt(rs, "owner_user_id"),
                            rs.getString("owner_username"),
                            nullableInt(rs, "linked_bill_id"),
                            nullableInt(rs, "linked_override_id"),
                            rs.getString("resolution_notes"),
                            rs.getString("reported_at"),
                            rs.getString("updated_at"),
                            rs.getString("resolved_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch pilot feedback tracker rows: " + e.getMessage());
        }
        return rows;
    }

    public List<String> getConfirmedAbuseMonitoringSubjects(LocalDate start, LocalDate end) {
        List<String> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT DISTINCT subject_reference " +
                "FROM (" +
                "SELECT LOWER('user:' || COALESCE(u.username, 'user-' || o.requested_by_user_id)) AS subject_reference " +
                "FROM subscription_pilot_feedback f " +
                "JOIN subscription_discount_overrides o ON o.override_id = f.linked_override_id " +
                "LEFT JOIN users u ON u.user_id = o.requested_by_user_id " +
                "WHERE f.reported_at >= ? AND f.reported_at < ? " +
                "AND UPPER(f.severity) IN ('HIGH', 'CRITICAL') " +
                "AND UPPER(f.status) = 'RESOLVED' " +
                "UNION " +
                "SELECT LOWER('plan:' || COALESCE(sp.plan_code, CAST(b.subscription_plan_id AS TEXT))) AS subject_reference " +
                "FROM subscription_pilot_feedback f " +
                "JOIN bills b ON b.bill_id = f.linked_bill_id " +
                "LEFT JOIN subscription_plans sp ON sp.plan_id = b.subscription_plan_id " +
                "WHERE f.reported_at >= ? AND f.reported_at < ? " +
                "AND UPPER(f.severity) IN ('HIGH', 'CRITICAL') " +
                "AND UPPER(f.status) = 'RESOLVED' " +
                "AND b.subscription_plan_id IS NOT NULL" +
                ") monitoring_subjects " +
                "WHERE subject_reference IS NOT NULL AND subject_reference <> ''";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            ps.setString(3, rangeStart);
            ps.setString(4, rangeEndExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString("subject_reference"));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch confirmed abuse monitoring subjects: " + e.getMessage());
        }
        return rows;
    }

    public List<EnrollmentMonitoringDecisionRow> getEnrollmentMonitoringDecisions(
            LocalDate start,
            LocalDate end,
            int limit) {
        List<EnrollmentMonitoringDecisionRow> rows = new ArrayList<>();
        if (start == null || end == null || start.isAfter(end)) {
            return rows;
        }

        int safeLimit = normalizeMonitoringLimit(limit);
        String rangeStart = start.atStartOfDay().format(DB_TIMESTAMP);
        String rangeEndExclusive = end.plusDays(1).atStartOfDay().format(DB_TIMESTAMP);
        String sql = "SELECT enrollment_id, customer_id, plan_id, start_date, created_at " +
                "FROM customer_subscriptions " +
                "WHERE created_at >= ? AND created_at < ? " +
                "ORDER BY created_at DESC, enrollment_id DESC " +
                "LIMIT ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rangeStart);
            ps.setString(2, rangeEndExclusive);
            ps.setInt(3, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new EnrollmentMonitoringDecisionRow(
                            rs.getInt("enrollment_id"),
                            rs.getInt("customer_id"),
                            rs.getInt("plan_id"),
                            rs.getString("start_date"),
                            rs.getString("created_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch enrollment monitoring decisions: " + e.getMessage());
        }
        return rows;
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

    private String classifyPricingAlertSeverity(String alertCode) {
        if (alertCode == null) {
            return "LOW";
        }
        return switch (alertCode) {
            case "NEGATIVE_SAVINGS",
                    "DISCOUNT_PERCENT_OUT_OF_RANGE",
                    "NEGATIVE_GROSS_BEFORE_DISCOUNT",
                    "SAVINGS_EXCEED_GROSS" -> "HIGH";
            case "DISCOUNT_PERCENT_MISMATCH" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String classifyOverrideAbuseSeverity(
            int totalRequests,
            double rejectionRatePercent,
            double averageRequestedPercent,
            double maxRequestedPercent) {
        boolean highRiskSignal = rejectionRatePercent >= 60.0
                || averageRequestedPercent >= 25.0
                || maxRequestedPercent >= 35.0;
        if (totalRequests >= 5 && highRiskSignal) {
            return "HIGH";
        }

        boolean mediumRiskSignal = rejectionRatePercent >= 40.0
                || averageRequestedPercent >= 20.0
                || maxRequestedPercent >= 30.0;
        if (totalRequests >= 3 && mediumRiskSignal) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String normalizeFeedbackCategory(String category) {
        if (category == null || category.isBlank()) {
            return "GENERAL";
        }
        String normalized = category.trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9_\\- ]", "");
        return normalized.isBlank() ? "GENERAL" : normalized;
    }

    private void upsertWeeklyAnalyticsSummary(
            Connection conn,
            String weekStartDate,
            String weekEndDate,
            String timezoneName,
            long totalBillCount,
            long subscriptionBillCount,
            double grossAmountBeforeDiscount,
            double netBilledAmount,
            double totalSavings,
            double leakagePercent,
            long activeSubscribersSnapshot,
            long renewalsDueNext7Days,
            long pendingOverrideCount,
            long highPricingAlertCount,
            long highOverrideAbuseSignalCount,
            long openHighCriticalFeedbackCount) throws SQLException {
        String sql = "INSERT INTO subscription_weekly_summary (" +
                "week_start_date, week_end_date, timezone_name, total_bill_count, subscription_bill_count, " +
                "gross_amount_before_discount, net_billed_amount, total_savings, leakage_percent, " +
                "active_subscribers_snapshot, renewals_due_next_7_days, pending_override_count, " +
                "high_pricing_alert_count, high_override_abuse_signal_count, open_high_critical_feedback_count" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (week_start_date, timezone_name) DO UPDATE SET " +
                "week_end_date = excluded.week_end_date, " +
                "total_bill_count = excluded.total_bill_count, " +
                "subscription_bill_count = excluded.subscription_bill_count, " +
                "gross_amount_before_discount = excluded.gross_amount_before_discount, " +
                "net_billed_amount = excluded.net_billed_amount, " +
                "total_savings = excluded.total_savings, " +
                "leakage_percent = excluded.leakage_percent, " +
                "active_subscribers_snapshot = excluded.active_subscribers_snapshot, " +
                "renewals_due_next_7_days = excluded.renewals_due_next_7_days, " +
                "pending_override_count = excluded.pending_override_count, " +
                "high_pricing_alert_count = excluded.high_pricing_alert_count, " +
                "high_override_abuse_signal_count = excluded.high_override_abuse_signal_count, " +
                "open_high_critical_feedback_count = excluded.open_high_critical_feedback_count, " +
                "generated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, weekStartDate);
            ps.setString(2, weekEndDate);
            ps.setString(3, timezoneName);
            ps.setLong(4, totalBillCount);
            ps.setLong(5, subscriptionBillCount);
            ps.setDouble(6, grossAmountBeforeDiscount);
            ps.setDouble(7, netBilledAmount);
            ps.setDouble(8, totalSavings);
            ps.setDouble(9, leakagePercent);
            ps.setLong(10, activeSubscribersSnapshot);
            ps.setLong(11, renewalsDueNext7Days);
            ps.setLong(12, pendingOverrideCount);
            ps.setLong(13, highPricingAlertCount);
            ps.setLong(14, highOverrideAbuseSignalCount);
            ps.setLong(15, openHighCriticalFeedbackCount);
            ps.executeUpdate();
        }
    }

    private void replaceWeeklyPlanRevenueSummary(
            Connection conn,
            String weekStartDate,
            String weekEndDate,
            String timezoneName,
            List<PlanRevenueImpactRow> planRows) throws SQLException {
        String deleteSql = "DELETE FROM subscription_weekly_plan_summary WHERE week_start_date = ? AND timezone_name = ?";
        String insertSql = "INSERT INTO subscription_weekly_plan_summary (" +
                "week_start_date, week_end_date, timezone_name, plan_id, plan_code, plan_name, bill_count, " +
                "gross_amount_before_discount, net_billed_amount, total_savings, average_savings_per_bill, leakage_percent" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
            deletePs.setString(1, weekStartDate);
            deletePs.setString(2, timezoneName);
            deletePs.executeUpdate();
        }

        if (planRows == null || planRows.isEmpty()) {
            return;
        }

        try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
            for (PlanRevenueImpactRow row : planRows) {
                insertPs.setString(1, weekStartDate);
                insertPs.setString(2, weekEndDate);
                insertPs.setString(3, timezoneName);
                insertPs.setInt(4, row.planId());
                insertPs.setString(5, row.planCode());
                insertPs.setString(6, row.planName());
                insertPs.setLong(7, row.billCount());
                insertPs.setDouble(8, row.grossAmountBeforeDiscount());
                insertPs.setDouble(9, row.netBilledAmount());
                insertPs.setDouble(10, row.totalSavings());
                insertPs.setDouble(11, row.averageSavingsPerBill());
                insertPs.setDouble(12, row.leakagePercent());
                insertPs.addBatch();
            }
            insertPs.executeBatch();
        }
    }

    private String normalizeFeedbackSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "MEDIUM";
        }
        String normalized = severity.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH", "CRITICAL" -> normalized;
            default -> "MEDIUM";
        };
    }

    private String normalizeFeedbackStatus(String status) {
        if (status == null || status.isBlank()) {
            return "OPEN";
        }
        String normalized = status.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "OPEN", "IN_PROGRESS", "RESOLVED" -> normalized;
            default -> "OPEN";
        };
    }

    private String normalizeFeedbackStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return "ALL";
        }
        String normalized = statusFilter.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "OPEN", "IN_PROGRESS", "RESOLVED", "ALL" -> normalized;
            default -> "ALL";
        };
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeTimezoneName(String timezoneName) {
        if (timezoneName == null || timezoneName.isBlank()) {
            return ZoneId.systemDefault().getId();
        }
        String candidate = timezoneName.trim();
        try {
            return ZoneId.of(candidate).getId();
        } catch (Exception ignored) {
            return ZoneId.systemDefault().getId();
        }
    }

    private int normalizeRecommendationLookbackDays(int lookbackDays) {
        if (lookbackDays <= 0) {
            return DEFAULT_RECOMMENDATION_LOOKBACK_DAYS;
        }
        return Math.max(MIN_RECOMMENDATION_LOOKBACK_DAYS, Math.min(MAX_RECOMMENDATION_LOOKBACK_DAYS, lookbackDays));
    }

    private int normalizeRenewalWindowDays(int renewalWindowDays) {
        if (renewalWindowDays <= 0) {
            return DEFAULT_RENEWAL_WINDOW_DAYS;
        }
        return Math.max(MIN_RENEWAL_WINDOW_DAYS, Math.min(MAX_RENEWAL_WINDOW_DAYS, renewalWindowDays));
    }

    private int normalizeMonitoringLimit(int limit) {
        if (limit <= 0) {
            return 200;
        }
        return Math.max(1, Math.min(1000, limit));
    }

    private long queryLong(Connection conn, String sql, SqlBinder binder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    private double queryDouble(Connection conn, String sql, SqlBinder binder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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

    public record CustomerPurchaseEvent(
            String billDate,
            double billedAmount) {
    }

    public record CustomerRefillEvent(
            int medicineId,
            String billDate,
            int quantity,
            double lineAmount) {
    }

    public record RenewalDueCandidate(
            int enrollmentId,
            int customerId,
            int planId,
            String planCode,
            String planName,
            String startDate,
            String endDate,
            double planPrice,
            int durationDays) {
    }

    public record EligibilityContext(
            CustomerSubscription enrollment,
            SubscriptionPlanStatus planStatus,
            String planName) {
    }

    public record OverrideFrequencySnapshot(
            int requestedByUserId,
            int totalRequests,
            int approvedCount,
            int rejectedCount,
            int pendingCount,
            String firstRequestAt,
            String latestRequestAt) {
    }

    public record SubscriptionDashboardSnapshot(
            long activeSubscribers,
            long renewalsDueSoon,
            double dailySubscriptionSavings,
            long pendingOverrideCount) {
    }

    public record PlanRevenueImpactRow(
            int planId,
            String planCode,
            String planName,
            long billCount,
            double grossAmountBeforeDiscount,
            double netBilledAmount,
            double totalSavings,
            double averageSavingsPerBill,
            double leakagePercent) {
    }

    public record DiscountLeakageRow(
            String billDay,
            long billCount,
            double grossAmountBeforeDiscount,
            double netBilledAmount,
            double totalSavings,
            double leakagePercent) {
    }

    public record WeeklyAnalyticsSummaryRow(
            String weekStartDate,
            String weekEndDate,
            String timezoneName,
            long totalBillCount,
            long subscriptionBillCount,
            double grossAmountBeforeDiscount,
            double netBilledAmount,
            double totalSavings,
            double leakagePercent,
            long activeSubscribersSnapshot,
            long renewalsDueNext7Days,
            long pendingOverrideCount,
            long highPricingAlertCount,
            long highOverrideAbuseSignalCount,
            long openHighCriticalFeedbackCount,
            String generatedAt) {
    }

    public record WeeklyLeakageHistoryRow(
            String weekStartDate,
            String weekEndDate,
            String timezoneName,
            double leakagePercent,
            double totalSavings,
            double grossAmountBeforeDiscount,
            String generatedAt) {
    }

    public record WeeklyPlanRevenueSummaryRow(
            String weekStartDate,
            String weekEndDate,
            String timezoneName,
            int planId,
            String planCode,
            String planName,
            long billCount,
            double grossAmountBeforeDiscount,
            double netBilledAmount,
            double totalSavings,
            double averageSavingsPerBill,
            double leakagePercent,
            String generatedAt) {
    }

    public record RejectedOverrideReportRow(
            int overrideId,
            Integer customerId,
            Integer enrollmentId,
            double requestedDiscountPercent,
            String requestReason,
            int requestedByUserId,
            String requestedByUsername,
            Integer rejectedByUserId,
            String rejectedByUsername,
            String requestedAt,
            String rejectedAt) {
    }

    public record PricingIntegrityAlertRow(
            int billId,
            String billDate,
            int planId,
            String planCode,
            String planName,
            double grossAmountBeforeDiscount,
            double netBilledAmount,
            double savingsAmount,
            double configuredDiscountPercent,
            double computedDiscountPercent,
            String alertCode,
            String severity) {
    }

    public record OverrideAbuseSignalRow(
            int requestedByUserId,
            String requestedByUsername,
            int totalRequests,
            int approvedCount,
            int rejectedCount,
            int pendingCount,
            double approvalRatePercent,
            double rejectionRatePercent,
            double averageRequestedPercent,
            double maxRequestedPercent,
            String firstRequestAt,
            String latestRequestAt,
            String severity) {
    }

    public record EnrollmentAbuseSignalRow(
            int customerId,
            int totalEvents,
            int planChangeCount,
            int cancellationCount,
            int freezeCount,
            int backdatedEnrollmentCount,
            int distinctPlanCount,
            String firstEventAt,
            String latestEventAt) {
    }

    public record EnrollmentMonitoringDecisionRow(
            int enrollmentId,
            int customerId,
            int enrolledPlanId,
            String enrollmentStartDate,
            String enrollmentCreatedAt) {
    }

    public record PilotFeedbackRow(
            int feedbackId,
            String category,
            String severity,
            String status,
            String title,
            String details,
            int reportedByUserId,
            String reportedByUsername,
            Integer ownerUserId,
            String ownerUsername,
            Integer linkedBillId,
            Integer linkedOverrideId,
            String resolutionNotes,
            String reportedAt,
            String updatedAt,
            String resolvedAt) {
    }
}
