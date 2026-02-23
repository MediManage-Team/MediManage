package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsReportDispatchDAO {
    private static final DateTimeFormatter DB_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_LIMIT = 100;

    public int createSchedule(
            String channel,
            String recipient,
            String reportFormat,
            String frequency,
            String timezoneName,
            LocalDate filterStartDate,
            LocalDate filterEndDate,
            String supplierFilter,
            String categoryFilter,
            Integer createdByUserId,
            LocalDateTime nextRunAt) {
        String safeChannel = normalizeChannel(channel);
        String safeRecipient = normalizeRequired(recipient, "recipient");
        String safeFormat = normalizeFormat(reportFormat);
        String safeFrequency = normalizeFrequency(frequency);
        String safeTimezone = normalizeTimezone(timezoneName);
        String safeSupplier = normalizeOptional(supplierFilter);
        String safeCategory = normalizeOptional(categoryFilter);
        LocalDateTime safeNextRun = nextRunAt == null ? LocalDateTime.now().plusHours(24) : nextRunAt;

        String sql = "INSERT INTO analytics_report_dispatch_schedules (" +
                "channel, recipient, report_format, frequency, timezone_name, " +
                "filter_start_date, filter_end_date, supplier_filter, category_filter, " +
                "active, next_run_at, created_by_user_id, last_status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'SCHEDULED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, safeChannel);
            ps.setString(2, safeRecipient);
            ps.setString(3, safeFormat);
            ps.setString(4, safeFrequency);
            ps.setString(5, safeTimezone);
            ps.setString(6, filterStartDate == null ? null : filterStartDate.toString());
            ps.setString(7, filterEndDate == null ? null : filterEndDate.toString());
            ps.setString(8, safeSupplier);
            ps.setString(9, safeCategory);
            ps.setString(10, safeNextRun.format(DB_TS));
            ps.setObject(11, createdByUserId);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create analytics report dispatch schedule", e);
        }
        throw new IllegalStateException("Failed to create analytics report dispatch schedule");
    }

    public List<DispatchScheduleRow> getDueSchedules(LocalDateTime asOf, int limit) {
        LocalDateTime safeAsOf = asOf == null ? LocalDateTime.now() : asOf;
        int safeLimit = normalizeLimit(limit);
        List<DispatchScheduleRow> rows = new ArrayList<>();

        String sql = "SELECT schedule_id, channel, recipient, report_format, frequency, timezone_name, " +
                "filter_start_date, filter_end_date, supplier_filter, category_filter, " +
                "active, next_run_at, last_run_at, last_status, last_error, created_by_user_id, created_at, updated_at " +
                "FROM analytics_report_dispatch_schedules " +
                "WHERE active = 1 AND next_run_at <= ? " +
                "ORDER BY next_run_at ASC, schedule_id ASC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeAsOf.format(DB_TS));
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("AnalyticsReportDispatchDAO.getDueSchedules: " + e.getMessage());
        }

        return rows;
    }

    public List<DispatchScheduleRow> getActiveSchedules(int limit) {
        int safeLimit = normalizeLimit(limit);
        List<DispatchScheduleRow> rows = new ArrayList<>();

        String sql = "SELECT schedule_id, channel, recipient, report_format, frequency, timezone_name, " +
                "filter_start_date, filter_end_date, supplier_filter, category_filter, " +
                "active, next_run_at, last_run_at, last_status, last_error, created_by_user_id, created_at, updated_at " +
                "FROM analytics_report_dispatch_schedules " +
                "WHERE active = 1 " +
                "ORDER BY next_run_at ASC, schedule_id ASC " +
                "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("AnalyticsReportDispatchDAO.getActiveSchedules: " + e.getMessage());
        }
        return rows;
    }

    public boolean markRunSuccess(int scheduleId, LocalDateTime runAt, LocalDateTime nextRunAt) {
        return markRunResult(scheduleId, runAt, nextRunAt, "SUCCESS", null);
    }

    public boolean markRunFailure(int scheduleId, LocalDateTime runAt, LocalDateTime nextRunAt, String error) {
        return markRunResult(scheduleId, runAt, nextRunAt, "FAILED", error);
    }

    public boolean markRunResult(
            int scheduleId,
            LocalDateTime runAt,
            LocalDateTime nextRunAt,
            String status,
            String error) {
        if (scheduleId <= 0) {
            return false;
        }
        LocalDateTime safeRunAt = runAt == null ? LocalDateTime.now() : runAt;
        LocalDateTime safeNextRunAt = nextRunAt == null ? safeRunAt.plusHours(24) : nextRunAt;
        String safeStatus = normalizeStatus(status);
        String safeError = normalizeOptional(error);

        String sql = "UPDATE analytics_report_dispatch_schedules " +
                "SET last_run_at = ?, next_run_at = ?, last_status = ?, last_error = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE schedule_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeRunAt.format(DB_TS));
            ps.setString(2, safeNextRunAt.format(DB_TS));
            ps.setString(3, safeStatus);
            ps.setString(4, safeError);
            ps.setInt(5, scheduleId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("AnalyticsReportDispatchDAO.markRunResult: " + e.getMessage());
            return false;
        }
    }

    public boolean deactivateSchedule(int scheduleId) {
        if (scheduleId <= 0) {
            return false;
        }
        String sql = "UPDATE analytics_report_dispatch_schedules " +
                "SET active = 0, updated_at = CURRENT_TIMESTAMP " +
                "WHERE schedule_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("AnalyticsReportDispatchDAO.deactivateSchedule: " + e.getMessage());
            return false;
        }
    }

    private DispatchScheduleRow mapRow(ResultSet rs) throws Exception {
        return new DispatchScheduleRow(
                rs.getInt("schedule_id"),
                rs.getString("channel"),
                rs.getString("recipient"),
                rs.getString("report_format"),
                rs.getString("frequency"),
                rs.getString("timezone_name"),
                rs.getString("filter_start_date"),
                rs.getString("filter_end_date"),
                rs.getString("supplier_filter"),
                rs.getString("category_filter"),
                rs.getInt("active") == 1,
                rs.getString("next_run_at"),
                rs.getString("last_run_at"),
                rs.getString("last_status"),
                rs.getString("last_error"),
                rs.getObject("created_by_user_id") == null ? null : rs.getInt("created_by_user_id"),
                rs.getString("created_at"),
                rs.getString("updated_at"));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 1000);
    }

    private String normalizeChannel(String value) {
        String normalized = normalizeRequired(value, "channel").toUpperCase();
        if ("EMAIL".equals(normalized) || "WHATSAPP".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported dispatch channel: " + value);
    }

    private String normalizeFormat(String value) {
        String normalized = normalizeRequired(value, "report format").toUpperCase();
        if ("PDF".equals(normalized) || "EXCEL".equals(normalized) || "CSV".equals(normalized)) {
            return normalized;
        }
        if ("XLSX".equals(normalized)) {
            return "EXCEL";
        }
        throw new IllegalArgumentException("Unsupported report format: " + value);
    }

    private String normalizeFrequency(String value) {
        String normalized = normalizeRequired(value, "frequency").toUpperCase();
        if ("DAILY".equals(normalized) || "WEEKLY".equals(normalized) || "MONTHLY".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported dispatch frequency: " + value);
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return "SCHEDULED";
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "SCHEDULED", "SUCCESS", "FAILED" -> normalized;
            default -> "SCHEDULED";
        };
    }

    private String normalizeTimezone(String timezoneName) {
        String safe = normalizeRequired(timezoneName, "timezone");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DispatchScheduleRow(
            int scheduleId,
            String channel,
            String recipient,
            String reportFormat,
            String frequency,
            String timezoneName,
            String filterStartDate,
            String filterEndDate,
            String supplierFilter,
            String categoryFilter,
            boolean active,
            String nextRunAt,
            String lastRunAt,
            String lastStatus,
            String lastError,
            Integer createdByUserId,
            String createdAt,
            String updatedAt) {
    }
}
