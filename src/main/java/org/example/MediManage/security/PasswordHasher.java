package org.example.MediManage.security;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {
    private static final int WORK_FACTOR = 12;

    private PasswordHasher() {
    }

    public static String hash(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        return BCrypt.hashpw(plainText, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean matches(String plainText, String storedValue) {
        if (plainText == null || storedValue == null || storedValue.isBlank()) {
            return false;
        }

        if (isBcryptHash(storedValue)) {
            try {
                return BCrypt.checkpw(plainText, storedValue);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        // Legacy compatibility for existing plaintext rows until they are migrated.
        return storedValue.equals(plainText);
    }

    public static boolean needsMigration(String storedValue) {
        return storedValue != null && !storedValue.isBlank() && !isBcryptHash(storedValue);
    }

    public static boolean isBcryptHash(String value) {
        if (value == null || value.length() != 60) {
            return false;
        }
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
