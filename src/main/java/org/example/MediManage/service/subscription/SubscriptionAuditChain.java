package org.example.MediManage.service.subscription;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class SubscriptionAuditChain {
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SubscriptionAuditChain() {
    }

    public static String computeChecksum(
            String eventType,
            String entityType,
            String entityId,
            Integer actorUserId,
            Integer approvalId,
            String reason,
            String beforeJson,
            String afterJson,
            String previousChecksum,
            String eventTimestamp) {
        String safePrevious = previousChecksum == null ? "" : previousChecksum;
        String safeTimestamp = eventTimestamp == null || eventTimestamp.isBlank()
                ? nowTimestamp()
                : eventTimestamp.trim();
        String payload = "v2|"
                + safe(eventType) + "|"
                + safe(entityType) + "|"
                + safe(entityId) + "|"
                + (actorUserId == null ? "" : actorUserId) + "|"
                + (approvalId == null ? "" : approvalId) + "|"
                + safe(reason) + "|"
                + safe(beforeJson) + "|"
                + safe(afterJson) + "|"
                + safeTimestamp + "|"
                + safePrevious;
        return sha256Hex(payload);
    }

    public static String nowTimestamp() {
        return LocalDateTime.now().format(EVENT_TIME_FORMAT);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute audit checksum.", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
