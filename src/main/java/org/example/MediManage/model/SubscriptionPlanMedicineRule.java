package org.example.MediManage.model;

public record SubscriptionPlanMedicineRule(
        int ruleId,
        int planId,
        int medicineId,
        String medicineName,
        boolean includeRule,
        double discountPercent,
        Double maxDiscountAmount,
        Double minMarginPercent,
        boolean active,
        String createdAt,
        String updatedAt) {
}
