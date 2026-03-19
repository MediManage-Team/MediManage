package org.example.MediManage.model;

public record AuditEvent(
        int eventId,
        String occurredAt,
        Integer actorUserId,
        String actorUsername,
        String eventType,
        String entityType,
        Integer entityId,
        String summary,
        String detailsJson) {
}
