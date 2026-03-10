package org.example.MediManage.security;



import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.Preferences;

public final class CloudApiKeyStore {

    public enum Provider {
        GEMINI, GROQ, OPENROUTER, OPENAI, CLAUDE
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
    private static final Map<Provider, String> LEGACY_PREF_KEYS =
            new EnumMap<>(Provider.class);

    static {
        LEGACY_PREF_KEYS.put(Provider.GEMINI, "cloud_api_key");
        LEGACY_PREF_KEYS.put(Provider.GROQ, "groq_api_key");
        LEGACY_PREF_KEYS.put(Provider.OPENROUTER, "openrouter_api_key");
        LEGACY_PREF_KEYS.put(Provider.OPENAI, "openai_api_key");
        LEGACY_PREF_KEYS.put(Provider.CLAUDE, "claude_api_key");
    }

    private CloudApiKeyStore() {
    }

    public static String get(Provider provider) {
        String secureKey = SecureSecretStore.get(secureAlias(provider));
        if (secureKey != null && !secureKey.isBlank()) {
            return secureKey;
        }

        // One-time migration from legacy plaintext Preferences keys.
        String legacyPref = LEGACY_PREF_KEYS.get(provider);
        String legacyKey = legacyPref == null ? "" : PREFS.get(legacyPref, "");
        if (legacyKey != null && !legacyKey.isBlank()) {
            put(provider, legacyKey);
            PREFS.remove(legacyPref);
            return legacyKey;
        }
        return "";
    }

    public static void put(Provider provider, String apiKey) {
        SecureSecretStore.put(secureAlias(provider), apiKey);
        String legacyPref = LEGACY_PREF_KEYS.get(provider);
        if (legacyPref != null) {
            PREFS.remove(legacyPref);
        }
    }

    private static String secureAlias(Provider provider) {
        return "cloud.apiKey." + provider.name().toLowerCase();
    }
}
