package org.example.MediManage.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationUtil.
 */
class ValidationUtilTest {

    @Test
    void testIsNullOrEmpty() {
        assertTrue(ValidationUtil.isNullOrEmpty(null));
        assertTrue(ValidationUtil.isNullOrEmpty(""));
        assertTrue(ValidationUtil.isNullOrEmpty("   "));
        assertFalse(ValidationUtil.isNullOrEmpty("test"));
    }

    @Test
    void testRequireNonEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.requireNonEmpty(null, "Field"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.requireNonEmpty("", "Field"));
        assertDoesNotThrow(() -> ValidationUtil.requireNonEmpty("value", "Field"));
    }

    @Test
    void testIsValidEmail() {
        assertTrue(ValidationUtil.isValidEmail("test@example.com"));
        assertTrue(ValidationUtil.isValidEmail("user.name@domain.co.uk"));
        assertFalse(ValidationUtil.isValidEmail("invalid.email"));
        assertFalse(ValidationUtil.isValidEmail("@example.com"));
        assertFalse(ValidationUtil.isValidEmail("test@"));
        assertFalse(ValidationUtil.isValidEmail(null));
        assertFalse(ValidationUtil.isValidEmail(""));
    }

    @Test
    void testIsValidPhone() {
        assertTrue(ValidationUtil.isValidPhone("1234567890"));
        assertTrue(ValidationUtil.isValidPhone("123-456-7890"));
        assertTrue(ValidationUtil.isValidPhone("(123) 456-7890"));
        assertFalse(ValidationUtil.isValidPhone("123"));
        assertFalse(ValidationUtil.isValidPhone("abcdefghij"));
        assertFalse(ValidationUtil.isValidPhone(null));
    }

    @Test
    void testIsValidDate() {
        assertTrue(ValidationUtil.isValidDate("2025-01-30"));
        assertTrue(ValidationUtil.isValidDate("2024-12-25"));
        assertFalse(ValidationUtil.isValidDate("30-01-2025"));
        assertFalse(ValidationUtil.isValidDate("2025/01/30"));
        assertFalse(ValidationUtil.isValidDate("invalid"));
        assertFalse(ValidationUtil.isValidDate(null));
    }

    @Test
    void testRequirePositive() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.requirePositive(0, "Value"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.requirePositive(-1, "Value"));
        assertDoesNotThrow(() -> ValidationUtil.requirePositive(1, "Value"));
    }

    @Test
    void testRequireInRange() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.requireInRange(5, 10, 20, "Value"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.requireInRange(25, 10, 20, "Value"));
        assertDoesNotThrow(() -> ValidationUtil.requireInRange(15, 10, 20, "Value"));
    }
}
