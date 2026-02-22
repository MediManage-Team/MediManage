package org.example.MediManage.model;

public enum SubscriptionEnrollmentStatus {
    ACTIVE,
    FROZEN,
    CANCELLED,
    EXPIRED;

    public static SubscriptionEnrollmentStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return SubscriptionEnrollmentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
