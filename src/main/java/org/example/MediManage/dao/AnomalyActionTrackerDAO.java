package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.util.ReportingWindowUtils;
import org.example.MediManage.util.WeeklyAnomalyAlertEvaluator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class AnomalyActionTrackerDAO {
    private static final int DEFAULT_LIMIT = 200;

    public List<ActionTrackerRow> syncAndGetWeeklyActions(
            LocalDate referenceDate,
            String timezoneName,
            List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts,
            Integer defaultOwnerUserId) {
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String safeTimezoneName = normalizeTimezoneName(timezoneName);
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(safeReferenceDate);
        String weekStartDate = weeklyWindow.startDate().toString();
        String weekEndDate = weeklyWindow.endDate().toString();
        String defaultDueDate = weeklyWindow.endDate().plusDays(2).toString();
        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> safeAlerts = alerts == null ? List.of() : alerts;

        String upsertSql = "INSERT INTO anomaly_action_tracker (" +
                "week_start_date, week_end_date, timezone_name, alert_type, severity, metric_value, threshold_rule, " +
                "alert_message, owner_user_id, due_date, closure_status, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', CURRENT_TIMESTAMP) " +
                "ON CONFLICT (week_start_date, timezone_name, alert_type) DO UPDATE SET " +
                "week_end_date = excluded.week_end_date, " +
                "severity = excluded.severity, " +
                "metric_value = excluded.metric_value, " +
                "threshold_rule = excluded.threshold_rule, " +
                "alert_message = excluded.alert_message, " +
                "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                for (WeeklyAnomalyAlertEvaluator.AnomalyAlert alert : safeAlerts) {
                    ps.setString(1, weekStartDate);
                    ps.setString(2, weekEndDate);
                    ps.setString(3, safeTimezoneName);
                    ps.setString(4, sanitize(alert.alertType()));
                    ps.setString(5, normalizeSeverity(alert.severity()));
                    ps.setString(6, sanitize(alert.metricValue()));
                    ps.setString(7, sanitize(alert.thresholdRule()));
                    ps.setString(8, sanitize(alert.message()));
                    ps.setObject(9, defaultOwnerUserId);
                    ps.setString(10, defaultDueDate);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            System.err.println("AnomalyActionTrackerDAO.syncAndGetWeeklyActions: " + e.getMessage());
        }

        return getWeeklyActions(safeReferenceDate, safeTimezoneName, DEFAULT_LIMIT);
    }

    public List<ActionTrackerRow> getWeeklyActions(LocalDate referenceDate, String timezoneName, int limit) {
        List<ActionTrackerRow> rows = new ArrayList<>();
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        String safeTimezoneName = normalizeTimezoneName(timezoneName);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 1000);
        ReportingWindowUtils.WeeklyWindow weeklyWindow = ReportingWindowUtils.mondayToSundayWindow(safeReferenceDate);
        String weekStartDate = weeklyWindow.startDate().toString();

        String sql = "SELECT a.action_id, a.week_start_date, a.week_end_date, a.timezone_name, " +
                "a.alert_type, a.severity, a.metric_value, a.threshold_rule, a.alert_message, " +
                "a.owner_user_id, u.username AS owner_username, a.due_date, a.closure_status, " +
                "a.closed_at, a.updated_at " +
                "FROM anomaly_action_tracker a " +
                "LEFT JOIN users u ON u.user_id = a.owner_user_id " +
                "WHERE a.week_start_date = ? AND a.timezone_name = ? " +
                "ORDER BY " +
                "CASE a.closure_status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END ASC, " +
                "CASE a.severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END ASC, " +
                "a.alert_type ASC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, weekStartDate);
            ps.setString(2, safeTimezoneName);
            ps.setInt(3, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ActionTrackerRow(
                            rs.getInt("action_id"),
                            rs.getString("week_start_date"),
                            rs.getString("week_end_date"),
                            rs.getString("timezone_name"),
                            rs.getString("alert_type"),
                            rs.getString("severity"),
                            rs.getString("metric_value"),
                            rs.getString("threshold_rule"),
                            rs.getString("alert_message"),
                            rs.getObject("owner_user_id") == null ? null : rs.getInt("owner_user_id"),
                            rs.getString("owner_username"),
                            rs.getString("due_date"),
                            rs.getString("closure_status"),
                            rs.getString("closed_at"),
                            rs.getString("updated_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("AnomalyActionTrackerDAO.getWeeklyActions: " + e.getMessage());
        }
        return rows;
    }

    public boolean updateAction(int actionId, Integer ownerUserId, LocalDate dueDate, String closureStatus) {
        if (actionId <= 0) {
            return false;
        }
        String safeStatus = normalizeStatus(closureStatus);
        String dueDateText = dueDate == null ? null : dueDate.toString();
        String sql = "UPDATE anomaly_action_tracker SET " +
                "owner_user_id = ?, due_date = ?, closure_status = ?, " +
                "closed_at = CASE WHEN ? = 'CLOSED' THEN CURRENT_TIMESTAMP ELSE NULL END, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE action_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ownerUserId);
            ps.setString(2, dueDateText);
            ps.setString(3, safeStatus);
            ps.setString(4, safeStatus);
            ps.setInt(5, actionId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("AnomalyActionTrackerDAO.updateAction: " + e.getMessage());
            return false;
        }
    }

    private String normalizeTimezoneName(String timezoneName) {
        if (timezoneName == null || timezoneName.isBlank()) {
            return ZoneId.systemDefault().getId();
        }
        return timezoneName.trim();
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "LOW";
        }
        String normalized = severity.trim().toUpperCase();
        if ("HIGH".equals(normalized) || "MEDIUM".equals(normalized) || "LOW".equals(normalized)) {
            return normalized;
        }
        return "LOW";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "OPEN";
        }
        String normalized = status.trim().toUpperCase();
        if ("OPEN".equals(normalized) || "IN_PROGRESS".equals(normalized) || "CLOSED".equals(normalized)) {
            return normalized;
        }
        return "OPEN";
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ActionTrackerRow(
            int actionId,
            String weekStartDate,
            String weekEndDate,
            String timezoneName,
            String alertType,
            String severity,
            String metricValue,
            String thresholdRule,
            String alertMessage,
            Integer ownerUserId,
            String ownerUsername,
            String dueDate,
            String closureStatus,
            String closedAt,
            String updatedAt) {
    }
}
