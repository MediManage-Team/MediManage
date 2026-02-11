package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Data Access Object for Expense entities.
 * Handles all database operations related to business expenses.
 */
public class ExpenseDAO {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseDAO.class);

    /**
     * Adds a new expense record to the database.
     * 
     * @param category    expense category
     * @param amount      expense amount
     * @param date        expense date (YYYY-MM-DD format)
     * @param description expense description
     * @throws SQLException             if database operation fails
     * @throws IllegalArgumentException if validation fails
     */
    public void addExpense(String category, double amount, String date, String description) throws SQLException {
        ValidationUtil.requireNonEmpty(category, "Category");
        ValidationUtil.requirePositive(amount, "Amount");
        ValidationUtil.requireNonEmpty(date, "Date");

        if (!ValidationUtil.isValidDate(date)) {
            throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD");
        }

        String sql = "INSERT INTO expenses (category, amount, date, description) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, date);
            pstmt.setString(4, description);
            pstmt.executeUpdate();

            logger.info("Added expense: {} - {} on {}", category, amount, date);

        } catch (SQLException e) {
            logger.error("Failed to add expense: category={}, amount={}, date={}", category, amount, date, e);
            throw e;
        }
    }

    /**
     * Gets total expenses for the current month.
     * 
     * @return total monthly expenses
     */
    public double getMonthlyExpenses() {
        String sql = "SELECT IFNULL(SUM(amount), 0) FROM expenses WHERE date(date) >= date('now', 'start of month')";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                double expenses = rs.getDouble(1);
                logger.debug("Monthly expenses: {}", expenses);
                return expenses;
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve monthly expenses", e);
            throw new RuntimeException("Failed to retrieve monthly expenses", e);
        }
        return 0.0;
    }
}
