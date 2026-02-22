package org.example.MediManage.model;

public enum SubscriptionDiscountOverrideStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED;

    public static SubscriptionDiscountOverrideStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return SubscriptionDiscountOverrideStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }
}
