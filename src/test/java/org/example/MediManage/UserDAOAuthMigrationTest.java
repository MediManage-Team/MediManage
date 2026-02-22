package org.example.MediManage;

import org.example.MediManage.dao.UserDAO;
import org.example.MediManage.model.User;
import org.example.MediManage.security.PasswordHasher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

public class UserDAOAuthMigrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-auth-tests-");
        testDbPath = tempDir.resolve("medimanage-auth-test.db");
        System.setProperty(DB_PATH_PROPERTY, testDbPath.toString());
        DatabaseUtil.initDB();
    }

    @AfterAll
    static void cleanupDb() {
        System.clearProperty(DB_PATH_PROPERTY);
        if (testDbPath == null) {
            return;
        }
        String baseName = testDbPath.getFileName().toString();
        tryDelete(testDbPath.resolveSibling(baseName + "-shm"));
        tryDelete(testDbPath.resolveSibling(baseName + "-wal"));
        tryDelete(testDbPath);
        Path parent = testDbPath.getParent();
        if (parent != null) {
            tryDelete(parent);
        }
    }

    @Test
    void authenticateMigrateLegacyPlaintextPassword() throws Exception {
        String username = "legacy_user_" + System.nanoTime();
        String legacyPassword = "legacy-pass-123";
        insertLegacyUser(username, legacyPassword, "ADMIN");

        UserDAO userDAO = new UserDAO();
        User authenticated = userDAO.authenticate(username, legacyPassword);

        assertNotNull(authenticated, "Legacy user should still authenticate during migration.");

        String storedAfterAuth = getStoredPassword(username);
        assertNotNull(storedAfterAuth);
        assertTrue(PasswordHasher.isBcryptHash(storedAfterAuth), "Password should be migrated to bcrypt.");
        assertNotEquals(legacyPassword, storedAfterAuth, "Stored password must no longer be plaintext.");
        assertTrue(PasswordHasher.matches(legacyPassword, storedAfterAuth), "Migrated hash must match original secret.");
    }

    @Test
    void authenticateRejectsWrongPasswordWithoutMigration() throws Exception {
        String username = "legacy_wrong_pw_" + System.nanoTime();
        String legacyPassword = "legacy-pass-xyz";
        insertLegacyUser(username, legacyPassword, "MANAGER");

        UserDAO userDAO = new UserDAO();
        User authenticated = userDAO.authenticate(username, "wrong-password");

        assertNull(authenticated, "Authentication should fail for wrong credentials.");
        assertEquals(legacyPassword, getStoredPassword(username), "Failed auth must not mutate stored password.");
    }

    private static void insertLegacyUser(String username, String password, String role) throws Exception {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, role);
            pstmt.executeUpdate();
        }
    }

    private static String getStoredPassword(String username) throws Exception {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password");
                }
            }
        }
        return null;
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }
}
