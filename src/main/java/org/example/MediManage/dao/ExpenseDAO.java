package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import java.time.LocalDate;
import java.sql.*;

public class ExpenseDAO {

    public void addExpense(String category, double amount, String date, String description) throws SQLException {
        String sql = "INSERT INTO expenses (category, amount, date, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, date);
            pstmt.setString(4, description);
            pstmt.executeUpdate();
        }
    }

    public double getMonthlyExpenses() {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses " +
                "WHERE date >= ? AND date < ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start.toString());
            stmt.setString(2, end.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }
}
