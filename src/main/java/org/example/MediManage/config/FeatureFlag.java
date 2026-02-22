package org.example.MediManage.config;

public enum FeatureFlag {
    AI_ASSISTANT("ai.assistant.enabled", true),
    POSTGRES_MIGRATION("database.postgres.migration.enabled", true),
    SUBSCRIPTION_COMMERCE("subscription.commerce.enabled", false),
    SUBSCRIPTION_APPROVALS("subscription.approvals.enabled", false),
    SUBSCRIPTION_DISCOUNT_OVERRIDES("subscription.discount.overrides.enabled", false);

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
