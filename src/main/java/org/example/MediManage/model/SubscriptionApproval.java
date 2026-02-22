package org.example.MediManage.model;

public record SubscriptionApproval(
        int approvalId,
        String approvalType,
        String requestRefType,
        int requestRefId,
        int requestedByUserId,
        Integer approverUserId,
        SubscriptionApprovalStatus approvalStatus,
        String reason,
        String approvedAt,
        String createdAt) {
}
