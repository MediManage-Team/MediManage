package org.example.MediManage.service.ai;

import org.example.MediManage.dao.AIPromptRegistryDAO;
import org.example.MediManage.model.AIPromptVersion;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AIPromptRegistryService {
    private static final String CHANGE_TYPE_UPDATE = "UPDATE";
    private static final String CHANGE_TYPE_ROLLBACK = "ROLLBACK";
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final AIPromptRegistryService INSTANCE = new AIPromptRegistryService(new AIPromptRegistryDAO());

    private final AIPromptRegistryDAO promptRegistryDAO;
    private final Map<String, String> activeTemplateCache = new ConcurrentHashMap<>();

    public static AIPromptRegistryService getInstance() {
        return INSTANCE;
    }

    AIPromptRegistryService(AIPromptRegistryDAO promptRegistryDAO) {
        this.promptRegistryDAO = promptRegistryDAO;
    }

    public String resolvePromptTemplate(String promptKey, String defaultTemplate) {
        String safeKey = normalizePromptKey(promptKey);
        String safeDefault = defaultTemplate == null ? "" : defaultTemplate;
        if (safeKey.isEmpty()) {
            return safeDefault;
        }

        String cached = activeTemplateCache.get(safeKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        try {
            Optional<AIPromptVersion> active = promptRegistryDAO.findActivePromptVersion(safeKey);
            AIPromptVersion version = active.orElseGet(() -> {
                try {
                    return promptRegistryDAO.seedPromptIfMissing(safeKey, safeDefault);
                } catch (SQLException seedEx) {
                    return null;
                }
            });

            if (version != null && version.templateText() != null && !version.templateText().isBlank()) {
                activeTemplateCache.put(safeKey, version.templateText());
                return version.templateText();
            }
        } catch (SQLException ignored) {
            // Fall back to hard-coded defaults for runtime continuity.
        }

        return safeDefault;
    }

    public AIPromptVersion registerPromptVersion(
            String promptKey,
            String templateText,
            String changeNote,
            Integer changedByUserId) throws SQLException {
        String safeKey = requirePromptKey(promptKey);
        String safeTemplate = requireTemplateText(templateText);
        String safeNote = normalizeChangeNote(changeNote);
        AIPromptVersion version = promptRegistryDAO.createPromptVersion(
                safeKey,
                safeTemplate,
                CHANGE_TYPE_UPDATE,
                safeNote,
                changedByUserId,
                null);
        activeTemplateCache.put(safeKey, version.templateText());
        return version;
    }

    public AIPromptVersion rollbackPromptVersion(
            String promptKey,
            int targetVersionNumber,
            String changeNote,
            Integer changedByUserId) throws SQLException {
        String safeKey = requirePromptKey(promptKey);
        if (targetVersionNumber <= 0) {
            throw new IllegalArgumentException("Target version number must be positive.");
        }

        AIPromptVersion target = promptRegistryDAO.findPromptVersion(safeKey, targetVersionNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Prompt version not found for key " + safeKey + " version " + targetVersionNumber));

        String safeNote = normalizeChangeNote(changeNote);
        AIPromptVersion rolledBack = promptRegistryDAO.createPromptVersion(
                safeKey,
                target.templateText(),
                CHANGE_TYPE_ROLLBACK,
                safeNote,
                changedByUserId,
                target.versionNumber());

        activeTemplateCache.put(safeKey, rolledBack.templateText());
        return rolledBack;
    }

    public List<AIPromptVersion> getPromptVersionHistory(String promptKey, int limit) throws SQLException {
        String safeKey = requirePromptKey(promptKey);
        int safeLimit = limit <= 0 ? DEFAULT_HISTORY_LIMIT : Math.max(1, Math.min(500, limit));
        return promptRegistryDAO.listPromptVersions(safeKey, safeLimit);
    }

    public void clearPromptCache(String promptKey) {
        String safeKey = normalizePromptKey(promptKey);
        if (safeKey.isEmpty()) {
            activeTemplateCache.clear();
            return;
        }
        activeTemplateCache.remove(safeKey);
    }

    private String normalizePromptKey(String promptKey) {
        return promptKey == null ? "" : promptKey.trim().toLowerCase();
    }

    private String requirePromptKey(String promptKey) {
        String safeKey = normalizePromptKey(promptKey);
        if (safeKey.isBlank()) {
            throw new IllegalArgumentException("Prompt key is required.");
        }
        return safeKey;
    }

    private String requireTemplateText(String templateText) {
        if (templateText == null || templateText.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt template text is required.");
        }
        return templateText.trim();
    }

    private String normalizeChangeNote(String changeNote) {
        if (changeNote == null || changeNote.trim().isEmpty()) {
            return "Prompt template updated.";
        }
        return changeNote.trim();
    }
}
