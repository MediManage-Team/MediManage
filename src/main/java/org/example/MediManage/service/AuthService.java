package org.example.MediManage.service;

import org.example.MediManage.config.DatabaseConfig;
import org.example.MediManage.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Authentication service with secure password handling using BCrypt.
 * Provides login functionality with hashed password verification.
 */
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    /**
     * Authenticates a user with username and password.
     * 
     * @param username the username
     * @param password the plain text password
     * @return true if authentication successful, false otherwise
     */
    public static boolean login(String username, String password) {
        if (username == null || password == null) {
            logger.warn("Login attempt with null credentials");
            return false;
        }

        String sql = "SELECT password FROM users WHERE username=?";

        try (Connection con = DatabaseConfig.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");

                // Check if password is hashed (BCrypt hashes start with $2a$ or $2b$)
                if (storedPassword.startsWith("$2")) {
                    // Verify hashed password
                    boolean verified = PasswordUtil.verifyPassword(password, storedPassword);
                    if (verified) {
                        logger.info("User '{}' logged in successfully", username);
                    } else {
                        logger.warn("Failed login attempt for user '{}'", username);
                    }
                    return verified;
                } else {
                    // Legacy plain text password (for backward compatibility during migration)
                    logger.warn("User '{}' has plain text password - please update", username);
                    boolean matches = password.equals(storedPassword);

                    if (matches) {
                        // Auto-migrate to hashed password
                        logger.info("Auto-migrating password for user '{}'", username);
                        updatePasswordHash(username, password);
                    }

                    return matches;
                }
            } else {
                logger.warn("Login attempt for non-existent user '{}'", username);
                return false;
            }

        } catch (SQLException e) {
            logger.error("Database error during login for user '{}'", username, e);
            return false;
        }
    }

    /**
     * Updates a user's password with a hashed version.
     * 
     * @param username    the username
     * @param newPassword the new plain text password
     * @return true if update successful, false otherwise
     */
    public static boolean updatePasswordHash(String username, String newPassword) {
        String sql = "UPDATE users SET password=? WHERE username=?";

        try (Connection con = DatabaseConfig.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            String hashedPassword = PasswordUtil.hashPassword(newPassword);
            ps.setString(1, hashedPassword);
            ps.setString(2, username);

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("Password updated successfully for user '{}'", username);
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Failed to update password for user '{}'", username, e);
            return false;
        }
    }

    /**
     * Creates a new user with hashed password.
     * 
     * @param username the username
     * @param password the plain text password
     * @param role     the user role
     * @return true if user created successfully, false otherwise
     */
    public static boolean createUser(String username, String password, String role) {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

        try (Connection con = DatabaseConfig.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            String hashedPassword = PasswordUtil.hashPassword(password);
            ps.setString(1, username);
            ps.setString(2, hashedPassword);
            ps.setString(3, role);

            int rowsInserted = ps.executeUpdate();
            if (rowsInserted > 0) {
                logger.info("User '{}' created successfully with role '{}'", username, role);
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Failed to create user '{}'", username, e);
            return false;
        }
    }
}
