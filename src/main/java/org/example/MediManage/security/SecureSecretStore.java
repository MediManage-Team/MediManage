package org.example.MediManage.security;

import com.sun.jna.platform.win32.Crypt32Util;
import org.example.MediManage.SettingsController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Stores small secrets in user Preferences with OS-backed encryption on Windows.
 * On non-Windows systems, a plain fallback is used to avoid breaking app flow.
 */
public final class SecureSecretStore {
    private static final String PREF_PREFIX = "secure.secret.";
    private static final String DPAPI_PREFIX = "dpapi:";
    private static final String PLAIN_PREFIX = "plain:";
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("win");
    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsController.class);

    private SecureSecretStore() {
    }

    public static void put(String key, String value) {
        String prefKey = prefKey(key);
        if (value == null || value.isBlank()) {
            PREFS.remove(prefKey);
            try { PREFS.flush(); } catch (Exception ignored) {}
            return;
        }

        String payload = encryptPayload(value);
        PREFS.put(prefKey, payload);
        try {
            PREFS.flush();
        } catch (Exception ignored) {}
    }

    public static String get(String key) {
        String stored = PREFS.get(prefKey(key), "");
        if (stored == null || stored.isBlank()) {
            return "";
        }

        try {
            if (stored.startsWith(DPAPI_PREFIX)) {
                String encoded = stored.substring(DPAPI_PREFIX.length());
                return decryptWindows(encoded);
            }
            if (stored.startsWith(PLAIN_PREFIX)) {
                return stored.substring(PLAIN_PREFIX.length());
            }
        } catch (Exception ignored) {
            return "";
        }

        // Legacy fallback for previously plaintext values in this secure namespace.
        return stored;
    }

    public static void remove(String key) {
        PREFS.remove(prefKey(key));
        try { PREFS.flush(); } catch (Exception ignored) {}
    }

    private static String encryptPayload(String value) {
        if (IS_WINDOWS) {
            try {
                byte[] protectedBytes = Crypt32Util.cryptProtectData(value.getBytes(StandardCharsets.UTF_8));
                return DPAPI_PREFIX + Base64.getEncoder().encodeToString(protectedBytes);
            } catch (Throwable ignored) {
                // Fall through to plain fallback if OS API is unavailable.
            }
        }
        return PLAIN_PREFIX + value;
    }

    private static String decryptWindows(String base64Ciphertext) {
        byte[] encrypted = Base64.getDecoder().decode(base64Ciphertext);
        byte[] clear = Crypt32Util.cryptUnprotectData(encrypted);
        return new String(clear, StandardCharsets.UTF_8);
    }

    private static String prefKey(String key) {
        return PREF_PREFIX + key;
    }
}
