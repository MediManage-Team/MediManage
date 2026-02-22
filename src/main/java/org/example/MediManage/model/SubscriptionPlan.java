package org.example.MediManage.model;

public record SubscriptionPlan(
        int planId,
        String planCode,
        String planName,
        String description,
        double price,
        int durationDays,
        int graceDays,
        double defaultDiscountPercent,
        double maxDiscountPercent,
        double minimumMarginPercent,
        SubscriptionPlanStatus status,
        boolean autoRenew,
        boolean requiresApproval,
        Integer createdByUserId,
        Integer updatedByUserId,
        String createdAt,
        String updatedAt) {

    public SubscriptionPlan withStatus(SubscriptionPlanStatus newStatus, Integer actorUserId) {
        return new SubscriptionPlan(
                planId,
                planCode,
                planName,
                description,
                price,
                durationDays,
                graceDays,
                defaultDiscountPercent,
                maxDiscountPercent,
                minimumMarginPercent,
                newStatus,
                autoRenew,
                requiresApproval,
                createdByUserId,
                actorUserId,
                createdAt,
                updatedAt);
    }
}
