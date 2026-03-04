package org.example.MediManage.util;

import java.util.regex.Pattern;

/**
 * Shared input validation utility. Centralises validation rules previously
 * scattered across controllers so that every form applies the same constraints.
 */
public final class InputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\-\\s]{7,15}$");

    private InputValidator() {
    }

    // ---- String checks ----

    /** Returns true if s is null, empty, or blank. */
    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Returns true if s is non-null and not blank. */
    public static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Validates a name: non-blank and at most maxLength characters (after trim).
     */
    public static boolean isValidName(String name, int maxLength) {
        return isNotBlank(name) && name.trim().length() <= maxLength;
    }

    /** Convenience: name must be non-blank and at most 100 characters. */
    public static boolean isValidName(String name) {
        return isValidName(name, 100);
    }

    // ---- Numeric checks ----

    /** Returns true if value is strictly greater than zero. */
    public static boolean isPositive(double value) {
        return value > 0;
    }

    /** Returns true if value is zero or greater. */
    public static boolean isNonNegative(double value) {
        return value >= 0;
    }

    /** Returns true if value is a positive integer (> 0). */
    public static boolean isPositiveInt(int value) {
        return value > 0;
    }

    /** Safely parses an integer. Returns null on failure. */
    public static Integer parseInteger(String text) {
        if (isBlank(text))
            return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Safely parses a double. Returns null on failure. */
    public static Double parseDouble(String text) {
        if (isBlank(text))
            return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Format checks ----

    /** Validates an email address format. */
    public static boolean isValidEmail(String email) {
        return isNotBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validates a phone number format (7-15 digits, optional +, hyphens, spaces).
     */
    public static boolean isValidPhone(String phone) {
        return isNotBlank(phone) && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /** Validates a price: must parse as a positive double. */
    public static boolean isValidPrice(String priceText) {
        Double price = parseDouble(priceText);
        return price != null && isPositive(price);
    }

    /** Validates a quantity: must parse as a positive integer. */
    public static boolean isValidQuantity(String quantityText) {
        Integer qty = parseInteger(quantityText);
        return qty != null && isPositiveInt(qty);
    }
}
