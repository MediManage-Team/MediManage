package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for employee check-in / check-out (attendance) tracking.
 */
public class AttendanceDAO {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Check in an employee. Returns the attendance_id.
     */
    public int checkIn(int userId) throws SQLException {
        String sql = "INSERT INTO employee_attendance (user_id, check_in_time, date) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, LocalDateTime.now().format(DT_FMT));
            ps.setString(3, LocalDate.now().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Check out an employee — sets check_out_time and calculates total_hours.
     */
    public boolean checkOut(int attendanceId) throws SQLException {
        String sql = "UPDATE employee_attendance SET check_out_time = ?, " +
                "total_hours = ROUND((JULIANDAY(?) - JULIANDAY(check_in_time)) * 24, 2) " +
                "WHERE attendance_id = ? AND check_out_time IS NULL";
        String now = LocalDateTime.now().format(DT_FMT);
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now);
            ps.setString(2, now);
            ps.setInt(3, attendanceId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Get today's active check-in for a user (no check-out yet).
     * Returns the attendance_id, or -1 if not checked in.
     */
    public int getActiveCheckIn(int userId) throws SQLException {
        String sql = "SELECT attendance_id FROM employee_attendance " +
                "WHERE user_id = ? AND date = ? AND check_out_time IS NULL " +
                "ORDER BY check_in_time DESC LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Get attendance records for a specific date.
     */
    public List<Map<String, Object>> getAttendanceByDate(String date) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT ea.attendance_id, ea.user_id, u.username, u.role, " +
                "ea.check_in_time, ea.check_out_time, ea.total_hours, ea.notes " +
                "FROM employee_attendance ea " +
                "JOIN users u ON ea.user_id = u.user_id " +
                "WHERE ea.date = ? ORDER BY ea.check_in_time";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("attendanceId", rs.getInt("attendance_id"));
                    m.put("userId", rs.getInt("user_id"));
                    m.put("username", rs.getString("username"));
                    m.put("role", rs.getString("role"));
                    m.put("checkIn", rs.getString("check_in_time"));
                    m.put("checkOut", rs.getString("check_out_time"));
                    m.put("totalHours", rs.getObject("total_hours"));
                    m.put("notes", rs.getString("notes"));
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    /**
     * Get attendance summary for a date range (for reports).
     */
    public List<Map<String, Object>> getAttendanceSummary(String startDate, String endDate) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT u.username, u.role, COUNT(ea.attendance_id) AS days_present, " +
                "COALESCE(SUM(ea.total_hours), 0) AS total_hours, " +
                "COALESCE(AVG(ea.total_hours), 0) AS avg_hours " +
                "FROM users u " +
                "LEFT JOIN employee_attendance ea ON ea.user_id = u.user_id " +
                "AND ea.date >= ? AND ea.date <= ? " +
                "WHERE u.active = 1 " +
                "GROUP BY u.user_id, u.username, u.role " +
                "ORDER BY total_hours DESC";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", rs.getString("username"));
                    m.put("role", rs.getString("role"));
                    m.put("daysPresent", rs.getInt("days_present"));
                    m.put("totalHours", rs.getDouble("total_hours"));
                    m.put("avgHours", rs.getDouble("avg_hours"));
                    rows.add(m);
                }
            }
        }
        return rows;
    }
}
