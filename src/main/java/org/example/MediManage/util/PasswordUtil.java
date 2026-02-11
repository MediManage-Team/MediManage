package org.example.MediManage.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Utility class for password hashing and verification using BCrypt.
 * Provides secure password management to replace plain text storage.
 */
public class PasswordUtil {

    // BCrypt cost factor (number of hashing rounds, 12 is a good balance of
    // security and performance)
    private static final int BCRYPT_COST = 12;

    /**
     * Hashes a plain text password using BCrypt.
     * 
     * @param plainPassword the plain text password to hash
     * @return the hashed password
     * @throws IllegalArgumentException if password is null or empty
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        return BCrypt.withDefaults().hashToString(BCRYPT_COST, plainPassword.toCharArray());
    }

    /**
     * Verifies a plain text password against a hashed password.
     * 
     * @param plainPassword  the plain text password to verify
     * @param hashedPassword the hashed password to check against
     * @return true if the password matches, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }

        BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword);
        return result.verified;
    }

    /**
     * Checks if a password meets minimum security requirements.
     * 
     * @param password the password to check
     * @return true if password meets requirements, false otherwise
     */
    public static boolean meetsMinimumRequirements(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        // Add more requirements as needed (e.g., uppercase, numbers, special chars)
        return true;
    }
}
