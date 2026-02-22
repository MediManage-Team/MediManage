package org.example.MediManage.model;

public enum SubscriptionApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public static SubscriptionApprovalStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return SubscriptionApprovalStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }
}
