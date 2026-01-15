package org.example.MediManage.dao;

import org.example.MediManage.DBUtil;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // Authenticate user
    public User authenticate(String username, String password) {
        // NOTE: Column is user_id, not id
        String sql = "SELECT user_id, username, password, role FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("user_id");
                    String user = rs.getString("username");
                    String pass = rs.getString("password");
                    String roleStr = rs.getString("role");
                    UserRole role = UserRole.fromString(roleStr);

                    return new User(id, user, pass, role);
                }
            }
        } catch (SQLException e) {
            System.err.println("Authentication error: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Get all users
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT user_id, username, password, role FROM users";

        try (Connection conn = DBUtil.getConnection();
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    // Add new user
    public void addUser(User user) {
        checkAdmin(); // Only ADMIN can add users
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPassword());
            pstmt.setString(3, user.getRole() != null ? user.getRole().name() : "STAFF"); // Default fallback if null

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Update existing user
    public void updateUser(User user) {
        checkAdmin(); // Only ADMIN can update users
        // Updates password and role. Username usually static or separate logic,
        // but here we can update it if needed. Requirement said: update password and
        // role.
        // But let's verify if username allows change. Typically yes.
        String sql = "UPDATE users SET username=?, password=?, role=? WHERE user_id=?";

        try (Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPassword());
            pstmt.setString(3, user.getRole().name());
            pstmt.setInt(4, user.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Delete user
    public void deleteUser(int userId) {
        checkAdmin(); // Only ADMIN can delete users
        String sql = "DELETE FROM users WHERE user_id=?";

        try (Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void checkAdmin() {
        if (!UserSession.getInstance().isLoggedIn()
                || UserSession.getInstance().getUser().getRole() != UserRole.ADMIN) {
            throw new SecurityException("Access Denied: Only administrators can perform this action.");
        }
    }
}
