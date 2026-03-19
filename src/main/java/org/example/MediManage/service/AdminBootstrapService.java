package org.example.MediManage.service;

import org.example.MediManage.model.UserRole;
import org.example.MediManage.security.PasswordHasher;
import org.example.MediManage.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public class AdminBootstrapService {
    private static final Logger LOGGER = Logger.getLogger(AdminBootstrapService.class.getName());
    private static final int MIN_PASSWORD_LENGTH = 10;
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";

    public boolean requiresBootstrap() throws SQLException {
        return DatabaseUtil.requiresAdminBootstrap();
    }

    public boolean createDefaultAdminIfMissing() throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            if (hasAnyAdmin(conn)) {
                return false;
            }

            insertAdmin(conn, DEFAULT_USERNAME, DEFAULT_PASSWORD);
        }

        LOGGER.warning("Seeded default admin account with username 'admin' and password 'admin'.");
        return true;
    }

    public void createInitialAdmin(String username, String password) throws SQLException {
        validateCredentials(username, password);

        try (Connection conn = DatabaseUtil.getConnection()) {
            if (hasAnyAdmin(conn)) {
                throw new IllegalStateException("An admin account already exists.");
            }

            insertAdmin(conn, username.trim(), password);
        }

        LOGGER.info("Created initial admin account through bootstrap flow.");
    }

    public void validateCredentials(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        String rawPassword = password == null ? "" : password;

        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (normalizedUsername.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        }
        if ("1".equals(normalizedUsername)) {
            throw new IllegalArgumentException("Username '1' is reserved and cannot be used.");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (rawPassword.equalsIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("Password cannot match the username.");
        }
        if (!containsLetter(rawPassword) || !containsDigit(rawPassword)) {
            throw new IllegalArgumentException("Password must include at least one letter and one number.");
        }
        if (allCharactersMatch(rawPassword)) {
            throw new IllegalArgumentException("Password must not repeat the same character.");
        }
    }

    private boolean hasAnyAdmin(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE UPPER(role) = 'ADMIN'");
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private void insertAdmin(Connection conn, String username, String password) throws SQLException {
        try (PreparedStatement stmt = conn
                .prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, PasswordHasher.hash(password));
            stmt.setString(3, UserRole.ADMIN.name());
            stmt.executeUpdate();
        }
    }

    private boolean containsLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean allCharactersMatch(String value) {
        if (value.isEmpty()) {
            return true;
        }
        char first = value.charAt(0);
        for (int i = 1; i < value.length(); i++) {
            if (value.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }
}
