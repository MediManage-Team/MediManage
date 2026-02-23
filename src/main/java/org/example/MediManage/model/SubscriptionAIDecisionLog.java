package org.example.MediManage.model;

public record SubscriptionAIDecisionLog(
        long decisionLogId,
        String decisionType,
        String subjectType,
        String subjectRef,
        String reasonCode,
        String reasonMessage,
        String decisionPayloadJson,
        String modelComponent,
        String modelVersion,
        String promptKey,
        Integer promptVersion,
        Integer actorUserId,
        String createdAt) {
}
