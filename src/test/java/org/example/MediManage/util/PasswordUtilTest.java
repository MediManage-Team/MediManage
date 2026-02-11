package org.example.MediManage.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordUtil.
 */
class PasswordUtilTest {

    @Test
    void testHashPassword() {
        String password = "testPassword123";
        String hashed = PasswordUtil.hashPassword(password);

        assertNotNull(hashed);
        assertNotEquals(password, hashed);
        assertTrue(hashed.startsWith("$2a$") || hashed.startsWith("$2b$"));
    }

    @Test
    void testHashPasswordThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(""));
    }

    @Test
    void testVerifyPassword() {
        String password = "mySecurePassword";
        String hashed = PasswordUtil.hashPassword(password);

        assertTrue(PasswordUtil.verifyPassword(password, hashed));
        assertFalse(PasswordUtil.verifyPassword("wrongPassword", hashed));
    }

    @Test
    void testVerifyPasswordWithNulls() {
        assertFalse(PasswordUtil.verifyPassword(null, "hash"));
        assertFalse(PasswordUtil.verifyPassword("password", null));
        assertFalse(PasswordUtil.verifyPassword(null, null));
    }

    @Test
    void testMeetsMinimumRequirements() {
        assertTrue(PasswordUtil.meetsMinimumRequirements("password123"));
        assertTrue(PasswordUtil.meetsMinimumRequirements("123456"));
        assertFalse(PasswordUtil.meetsMinimumRequirements("12345")); // < 6 chars
        assertFalse(PasswordUtil.meetsMinimumRequirements(null));
    }

    @Test
    void testHashesAreDifferent() {
        String password = "samePassword";
        String hash1 = PasswordUtil.hashPassword(password);
        String hash2 = PasswordUtil.hashPassword(password);

        // Each hash should be different due to salt
        assertNotEquals(hash1, hash2);

        // But both should verify correctly
        assertTrue(PasswordUtil.verifyPassword(password, hash1));
        assertTrue(PasswordUtil.verifyPassword(password, hash2));
    }
}
