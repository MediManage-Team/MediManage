package org.example.MediManage.model;

public record SupervisorApproval(
        int approvalId,
        Integer requestedByUserId,
        int approvedByUserId,
        String approvedByUsername,
        String actionType,
        String entityType,
        Integer entityId,
        String justification,
        String approvalNotes,
        String approvedAt) {
}
