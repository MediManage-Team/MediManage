package org.example.MediManage.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Centralized validation utility for input validation across the application.
 * Provides common validation methods for email, phone, dates, and other inputs.
 */
public class ValidationUtil {

    private static final Logger logger = LoggerFactory.getLogger(ValidationUtil.class);

    // Email pattern: basic email format validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    // Phone pattern: supports various formats like 1234567890, 123-456-7890, (123)
    // 456-7890
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[\\d\\s()+-]{10,15}$");

    // Date pattern: YYYY-MM-DD format
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}$");

    /**
     * Checks if a string is null or empty (after trimming).
     * 
     * @param value the string to check
     * @return true if null or empty, false otherwise
     */
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Validates if a string is not null or empty.
     * 
     * @param value     the string to validate
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if validation fails
     */
    public static void requireNonEmpty(String value, String fieldName) {
        if (isNullOrEmpty(value)) {
            logger.warn("Validation failed: {} is required", fieldName);
            throw new IllegalArgumentException(fieldName + " is required and cannot be empty");
        }
    }

    /**
     * Validates email format.
     * 
     * @param email the email to validate
     * @return true if valid email format, false otherwise
     */
    public static boolean isValidEmail(String email) {
        if (isNullOrEmpty(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validates and requires a valid email format.
     * 
     * @param email the email to validate
     * @throws IllegalArgumentException if email format is invalid
     */
    public static void requireValidEmail(String email) {
        if (!isValidEmail(email)) {
            logger.warn("Validation failed: Invalid email format - {}", email);
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
    }

    /**
     * Validates phone number format.
     * 
     * @param phone the phone number to validate
     * @return true if valid phone format, false otherwise
     */
    public static boolean isValidPhone(String phone) {
        if (isNullOrEmpty(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Validates date format (YYYY-MM-DD).
     * 
     * @param date the date string to validate
     * @return true if valid date format, false otherwise
     */
    public static boolean isValidDate(String date) {
        if (isNullOrEmpty(date)) {
            return false;
        }
        return DATE_PATTERN.matcher(date.trim()).matches();
    }

    /**
     * Validates that a number is within a specified range.
     * 
     * @param value     the value to check
     * @param min       minimum allowed value (inclusive)
     * @param max       maximum allowed value (inclusive)
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is out of range
     */
    public static void requireInRange(double value, double min, double max, String fieldName) {
        if (value < min || value > max) {
            logger.warn("Validation failed: {} ({}) is out of range [{}, {}]", fieldName, value, min, max);
            throw new IllegalArgumentException(
                    fieldName + " must be between " + min + " and " + max + ", got: " + value);
        }
    }

    /**
     * Validates that a number is positive.
     * 
     * @param value     the value to check
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is not positive
     */
    public static void requirePositive(double value, String fieldName) {
        if (value <= 0) {
            logger.warn("Validation failed: {} ({}) must be positive", fieldName, value);
            throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
        }
    }

    /**
     * Validates that an integer is positive.
     * 
     * @param value     the value to check
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is not positive
     */
    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            logger.warn("Validation failed: {} ({}) must be positive", fieldName, value);
            throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
        }
    }

    /**
     * Validates that a number is non-negative.
     * 
     * @param value     the value to check
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is negative
     */
    public static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            logger.warn("Validation failed: {} ({}) must be non-negative", fieldName, value);
            throw new IllegalArgumentException(fieldName + " must be non-negative, got: " + value);
        }
    }
}
