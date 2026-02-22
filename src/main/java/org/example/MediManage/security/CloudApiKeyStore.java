package org.example.MediManage.security;

import org.example.MediManage.SettingsController;
import org.example.MediManage.service.ai.CloudAIService;

import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.Preferences;

public final class CloudApiKeyStore {
    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsController.class);
    private static final Map<CloudAIService.Provider, String> LEGACY_PREF_KEYS =
            new EnumMap<>(CloudAIService.Provider.class);

    static {
        LEGACY_PREF_KEYS.put(CloudAIService.Provider.GEMINI, "cloud_api_key");
        LEGACY_PREF_KEYS.put(CloudAIService.Provider.GROQ, "groq_api_key");
        LEGACY_PREF_KEYS.put(CloudAIService.Provider.OPENROUTER, "openrouter_api_key");
        LEGACY_PREF_KEYS.put(CloudAIService.Provider.OPENAI, "openai_api_key");
        LEGACY_PREF_KEYS.put(CloudAIService.Provider.CLAUDE, "claude_api_key");
    }

    private CloudApiKeyStore() {
    }

    public static String get(CloudAIService.Provider provider) {
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

    public static void put(CloudAIService.Provider provider, String apiKey) {
        SecureSecretStore.put(secureAlias(provider), apiKey);
        String legacyPref = LEGACY_PREF_KEYS.get(provider);
        if (legacyPref != null) {
            PREFS.remove(legacyPref);
        }
    }

    private static String secureAlias(CloudAIService.Provider provider) {
        return "cloud.apiKey." + provider.name().toLowerCase();
    }
}
