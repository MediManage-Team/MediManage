package org.example.MediManage.config;

public enum FeatureFlag {
    AI_ASSISTANT("ai.assistant.enabled", true);

    private final String key;
    private final boolean defaultEnabled;

    FeatureFlag(String key, boolean defaultEnabled) {
        this.key = key;
        this.defaultEnabled = defaultEnabled;
    }

    public String key() {
        return key;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }
}
