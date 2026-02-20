package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
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
        // SQLite: Calculate total expenses for current month
        // date('now', 'start of month') -> first day of current month
        String sql = "SELECT IFNULL(SUM(amount), 0) FROM expenses WHERE date(date) >= date('now', 'start of month')";
        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }
}
