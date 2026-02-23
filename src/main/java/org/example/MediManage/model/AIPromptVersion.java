package org.example.MediManage.model;

public record AIPromptVersion(
        long promptVersionId,
        String promptKey,
        int versionNumber,
        String templateText,
        String changeType,
        String changeNote,
        Integer rolledBackFromVersion,
        boolean active,
        Integer changedByUserId,
        String createdAt) {
}
