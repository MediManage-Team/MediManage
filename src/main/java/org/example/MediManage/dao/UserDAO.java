package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.service.AuthService;
import org.example.MediManage.util.PasswordUtil;
import org.example.MediManage.util.UserSession;
import org.example.MediManage.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for User entities.
 * Handles all database operations related to users with proper security and
 * validation.
 * 
 * SECURITY NOTE: This DAO integrates with AuthService for secure password
 * handling.
 * Passwords are hashed using BCrypt before storage.
 */
public class UserDAO {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

    /**
     * Authenticates a user with username and password.
     * This method delegates to AuthService for secure authentication.
     * 
     * @param username the username
     * @param password the plain text password
     * @return User object if authentication successful, null otherwise
     * @throws SQLException if database operation fails
     */
    public User authenticate(String username, String password) throws SQLException {
        ValidationUtil.requireNonEmpty(username, "Username");
        ValidationUtil.requireNonEmpty(password, "Password");

        // Use AuthService for secure authentication
        boolean authenticated = AuthService.login(username, password);

        if (!authenticated) {
            logger.warn("Failed authentication attempt for username: {}", username);
            return null;
        }

        // If authenticated, fetch user details
        String sql = "SELECT user_id, username, password, role FROM users WHERE username = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("user_id");
                    String user = rs.getString("username");
                    String pass = rs.getString("password");
                    String roleStr = rs.getString("role");
                    UserRole role = UserRole.fromString(roleStr);

                    logger.info("User '{}' authenticated successfully with role {}", username, role);
                    return new User(id, user, pass, role);
                }
            }
        } catch (SQLException e) {
            logger.error("Database error during authentication for user '{}'", username, e);
            throw e;
        }

        return null;
    }

    /**
     * Gets all users from the database.
     * 
     * @return list of all users
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT user_id, username, password, role FROM users";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("user_id");
                String username = rs.getString("username");
                String password = rs.getString("password");
                String roleStr = rs.getString("role");
                UserRole role = UserRole.fromString(roleStr);

                users.add(new User(id, username, password, role));
            }
            logger.debug("Retrieved {} users from database", users.size());

        } catch (SQLException e) {
            logger.error("Failed to retrieve users", e);
            throw new RuntimeException("Failed to retrieve users", e);
        }
        return users;
    }

    /**
     * Adds a new user to the database with hashed password.
     * SECURITY: Passwords are automatically hashed using BCrypt.
     * Only ADMIN users can add new users.
     * 
     * @param user the user to add
     * @throws SecurityException if current user is not admin
     * @throws SQLException      if database operation fails
     */
    public void addUser(User user) throws SQLException {
        checkAdmin();

        if (user == null) {
            throw new IllegalArgumentException("User object cannot be null");
        }

        ValidationUtil.requireNonEmpty(user.getUsername(), "Username");
        ValidationUtil.requireNonEmpty(user.getPassword(), "Password");

        // Validate password strength
        if (!PasswordUtil.meetsMinimumRequirements(user.getPassword())) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }

        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String hashedPassword = PasswordUtil.hashPassword(user.getPassword());

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, hashedPassword); // Store hashed password
            pstmt.setString(3, user.getRole() != null ? user.getRole().name() : "CASHIER");

            pstmt.executeUpdate();
            logger.info("Added new user: {} with role {}", user.getUsername(), user.getRole());

        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                logger.warn("Attempted to create duplicate username: {}", user.getUsername());
                throw new SQLException("Username already exists: " + user.getUsername());
            }
            logger.error("Failed to add user: {}", user.getUsername(), e);
            throw e;
        }
    }

    /**
     * Updates an existing user's information.
     * SECURITY: If password is being updated, it will be hashed.
     * Only ADMIN users can update users.
     * 
     * @param user the user with updated information
     * @throws SecurityException if current user is not admin
     * @throws SQLException      if database operation fails
     */
    public void updateUser(User user) throws SQLException {
        checkAdmin();

        if (user == null) {
            throw new IllegalArgumentException("User object cannot be null");
        }

        ValidationUtil.requireNonEmpty(user.getUsername(), "Username");
        ValidationUtil.requirePositive(user.getId(), "User ID");

        String sql = "UPDATE users SET username=?, password=?, role=? WHERE user_id=?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String password = user.getPassword();

            // If password doesn't look like a BCrypt hash, hash it
            if (password != null && !password.startsWith("$2")) {
                if (!PasswordUtil.meetsMinimumRequirements(password)) {
                    throw new IllegalArgumentException("Password must be at least 6 characters long");
                }
                password = PasswordUtil.hashPassword(password);
            }

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, password);
            pstmt.setString(3, user.getRole().name());
            pstmt.setInt(4, user.getId());

            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                logger.warn("No user found with ID: {}", user.getId());
                throw new SQLException("User ID " + user.getId() + " not found");
            }
            logger.info("Updated user: {} (ID: {})", user.getUsername(), user.getId());

        } catch (SQLException e) {
            logger.error("Failed to update user ID: {}", user.getId(), e);
            throw e;
        }
    }

    /**
     * Deletes a user from the database.
     * Only ADMIN users can delete users.
     * 
     * @param userId the ID of the user to delete
     * @throws SecurityException if current user is not admin
     * @throws SQLException      if database operation fails
     */
    public void deleteUser(int userId) throws SQLException {
        checkAdmin();

        ValidationUtil.requirePositive(userId, "User ID");

        // Prevent deleting the last admin
        if (isLastAdmin(userId)) {
            throw new SecurityException("Cannot delete the last administrator account");
        }

        String sql = "DELETE FROM users WHERE user_id=?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            int deleted = pstmt.executeUpdate();

            if (deleted == 0) {
                logger.warn("No user found with ID: {}", userId);
                throw new SQLException("User ID " + userId + " not found");
            }
            logger.info("Deleted user ID: {}", userId);

        } catch (SQLException e) {
            logger.error("Failed to delete user ID: {}", userId, e);
            throw e;
        }
    }

    /**
     * Checks if a user is the last admin in the system.
     * 
     * @param userId the user ID to check
     * @return true if this is the last admin, false otherwise
     */
    private boolean isLastAdmin(int userId) {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
        String checkUserRoleSql = "SELECT role FROM users WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Check if user is admin
            try (PreparedStatement ps = conn.prepareStatement(checkUserRoleSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && !"ADMIN".equals(rs.getString("role"))) {
                        return false; // Not an admin, so not the last admin
                    }
                }
            }

            // Count total admins
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    int adminCount = rs.getInt(1);
                    return adminCount == 1;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking admin count", e);
        }
        return false;
    }

    /**
     * Checks if the current user has admin privileges.
     * 
     * @throws SecurityException if user is not admin
     */
    private void checkAdmin() {
        if (!UserSession.getInstance().isLoggedIn()
                || UserSession.getInstance().getUser().getRole() != UserRole.ADMIN) {
            logger.warn("Unauthorized access attempt - user is not admin");
            throw new SecurityException("Access Denied: Only administrators can perform this action.");
        }
    }
}
