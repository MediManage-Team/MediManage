package org.example.MediManage.model;

public record SubscriptionDiscountOverride(
        int overrideId,
        Integer billId,
        Integer billItemId,
        Integer customerId,
        Integer enrollmentId,
        double requestedDiscountPercent,
        Double approvedDiscountPercent,
        SubscriptionDiscountOverrideStatus status,
        String reason,
        int requestedByUserId,
        Integer approvedByUserId,
        Integer approvalId,
        String createdAt,
        String approvedAt) {
}
