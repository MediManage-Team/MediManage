package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.security.PasswordHasher;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // Authenticate user
    public User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT user_id, username, password, role FROM users WHERE username = ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("user_id");
                    String user = rs.getString("username");
                    String storedPassword = rs.getString("password");
                    String roleStr = rs.getString("role");
                    UserRole role = UserRole.fromString(roleStr);

                    if (!PasswordHasher.matches(password, storedPassword)) {
                        return null;
                    }

                    // Backward-compatible migration for legacy plaintext passwords.
                    if (PasswordHasher.needsMigration(storedPassword)) {
                        updatePasswordHash(conn, id, PasswordHasher.hash(password));
                    }

                    // Never expose password values to higher layers.
                    return new User(id, user, "", role);
                }
            }
        }
        return null;
    }

    // Get all users
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT user_id, username, role FROM users";

        try (Connection conn = DatabaseUtil.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("user_id");
                String username = rs.getString("username");
                String roleStr = rs.getString("role");
                UserRole role = UserRole.fromString(roleStr);

                users.add(new User(id, username, "", role));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    // Add new user
    public void addUser(User user) {
        checkAdmin(); // Only ADMIN can add users
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        String rawPassword = user.getPassword();

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, PasswordHasher.hash(rawPassword));
            pstmt.setString(3, user.getRole() != null ? user.getRole().name() : "STAFF"); // Default fallback if null

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Update existing user
    public void updateUser(User user) {
        checkAdmin(); // Only ADMIN can update users
        boolean hasPasswordUpdate = user.getPassword() != null && !user.getPassword().isBlank();
        String sql = hasPasswordUpdate
                ? "UPDATE users SET username=?, password=?, role=? WHERE user_id=?"
                : "UPDATE users SET username=?, role=? WHERE user_id=?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            int paramIndex = 2;

            if (hasPasswordUpdate) {
                pstmt.setString(paramIndex++, PasswordHasher.hash(user.getPassword()));
            }

            pstmt.setString(paramIndex++, user.getRole().name());
            pstmt.setInt(paramIndex, user.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Delete user
    public void deleteUser(int userId) {
        checkAdmin(); // Only ADMIN can delete users
        String sql = "DELETE FROM users WHERE user_id=?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void checkAdmin() {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_USERS);
    }

    private void updatePasswordHash(Connection conn, int userId, String hashedPassword) throws SQLException {
        String sql = "UPDATE users SET password=? WHERE user_id=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hashedPassword);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }
}
