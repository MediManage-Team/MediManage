package org.example.MediManage.model;

public enum SubscriptionPlanStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    RETIRED;

    public static SubscriptionPlanStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return SubscriptionPlanStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DRAFT;
        }
    }
}
