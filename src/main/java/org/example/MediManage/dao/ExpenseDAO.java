package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import java.time.LocalDate;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.example.MediManage.model.Expense;

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

    public List<Expense> getAllExpenses() throws SQLException {
        List<Expense> list = new ArrayList<>();
        String sql = "SELECT * FROM expenses ORDER BY date DESC, expense_id DESC";
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Expense(
                        rs.getInt("expense_id"),
                        rs.getString("category"),
                        rs.getDouble("amount"),
                        rs.getString("date"),
                        rs.getString("description")
                ));
            }
        }
        return list;
    }

    public void deleteExpense(int id) throws SQLException {
        String sql = "DELETE FROM expenses WHERE expense_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BUSINESS INTELLIGENCE QUERIES
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns total expenses grouped by category.
     */
    public java.util.Map<String, Double> getExpensesByCategory() {
        java.util.Map<String, Double> result = new java.util.LinkedHashMap<>();
        String sql = "SELECT category, SUM(amount) as total FROM expenses GROUP BY category ORDER BY total DESC";
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("category"), rs.getDouble("total"));
            }
        } catch (SQLException e) {
            System.err.println("getExpensesByCategory error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns monthly expense totals for the last N months.
     */
    public List<java.util.AbstractMap.SimpleEntry<String, Double>> getMonthlyExpenseTrend(int months) {
        List<java.util.AbstractMap.SimpleEntry<String, Double>> trend = new ArrayList<>();
        String sql = "SELECT strftime('%Y-%m', date) as month, SUM(amount) as total " +
                "FROM expenses WHERE date >= date('now', '-' || ? || ' months') " +
                "GROUP BY month ORDER BY month ASC";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, months);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trend.add(new java.util.AbstractMap.SimpleEntry<>(rs.getString("month"), rs.getDouble("total")));
                }
            }
        } catch (SQLException e) {
            System.err.println("getMonthlyExpenseTrend error: " + e.getMessage());
        }
        return trend;
    }
}
