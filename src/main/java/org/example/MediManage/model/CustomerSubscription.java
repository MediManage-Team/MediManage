package org.example.MediManage.model;

public record CustomerSubscription(
        int enrollmentId,
        int customerId,
        int planId,
        SubscriptionEnrollmentStatus status,
        String startDate,
        String endDate,
        String graceEndDate,
        String enrollmentChannel,
        Integer enrolledByUserId,
        Integer approvedByUserId,
        String approvalReference,
        String cancellationReason,
        String frozenReason,
        String createdAt,
        String updatedAt) {
}
