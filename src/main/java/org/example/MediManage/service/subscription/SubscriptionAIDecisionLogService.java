package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionAIDecisionLogDAO;
import org.example.MediManage.model.SubscriptionAIDecisionLog;

public class SubscriptionAIDecisionLogService {
    private static final int MAX_TEXT_LENGTH = 500;
    private static final SubscriptionAIDecisionLogService INSTANCE =
            new SubscriptionAIDecisionLogService(new SubscriptionAIDecisionLogDAO());

    private final SubscriptionAIDecisionLogDAO decisionLogDAO;

    public static SubscriptionAIDecisionLogService getInstance() {
        return INSTANCE;
    }

    public SubscriptionAIDecisionLogService(SubscriptionAIDecisionLogDAO decisionLogDAO) {
        this.decisionLogDAO = decisionLogDAO;
    }

    public void logDecision(
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
            Integer actorUserId) {
        String safeDecisionType = normalizeCode(decisionType, "UNKNOWN_DECISION");
        String safeSubjectType = normalizeCode(subjectType, "UNKNOWN_SUBJECT");
        String safeSubjectRef = normalizeSubjectRef(subjectRef);
        String safeReasonCode = normalizeCode(reasonCode, "UNSPECIFIED_REASON");
        String safeReasonMessage = normalizeText(reasonMessage, "No reason message provided.");
        String safePayload = normalizePayload(decisionPayloadJson);
        String safeModelComponent = normalizeText(modelComponent, null);
        String safeModelVersion = normalizeText(modelVersion, null);
        String safePromptKey = normalizePromptKey(promptKey);

        try {
            decisionLogDAO.appendDecisionLog(new SubscriptionAIDecisionLog(
                    0L,
                    safeDecisionType,
                    safeSubjectType,
                    safeSubjectRef,
                    safeReasonCode,
                    safeReasonMessage,
                    safePayload,
                    safeModelComponent,
                    safeModelVersion,
                    safePromptKey,
                    promptVersion,
                    actorUserId,
                    null));
        } catch (Exception e) {
            System.err.println("Failed to append subscription AI decision log: " + e.getMessage());
        }
    }

    private String normalizeCode(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        String cleaned = raw.trim().toUpperCase()
                .replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("_+", "_");
        if (cleaned.startsWith("_")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("_")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isBlank()) {
            return fallback;
        }
        return cleaned.length() <= MAX_TEXT_LENGTH ? cleaned : cleaned.substring(0, MAX_TEXT_LENGTH);
    }

    private String normalizeSubjectRef(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "n/a";
        }
        String value = raw.trim();
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    private String normalizeText(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        String value = raw.trim();
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    private String normalizePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            return "{}";
        }
        String value = payloadJson.trim();
        return value.length() <= 4_000 ? value : value.substring(0, 4_000);
    }

    private String normalizePromptKey(String promptKey) {
        if (promptKey == null || promptKey.trim().isEmpty()) {
            return null;
        }
        return promptKey.trim().toLowerCase();
    }
}
