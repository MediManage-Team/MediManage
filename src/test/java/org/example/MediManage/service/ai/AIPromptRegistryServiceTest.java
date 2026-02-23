package org.example.MediManage.service.ai;

import org.example.MediManage.dao.AIPromptRegistryDAO;
import org.example.MediManage.model.AIPromptVersion;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIPromptRegistryServiceTest {

    @Test
    void resolvePromptTemplateSeedsDefaultWhenMissing() {
        FakeAIPromptRegistryDAO dao = new FakeAIPromptRegistryDAO();
        AIPromptRegistryService service = new AIPromptRegistryService(dao);

        String template = service.resolvePromptTemplate("checkout_care_protocol", "Default template text");

        assertEquals("Default template text", template);
        List<AIPromptVersion> history = dao.listUnsafe("checkout_care_protocol");
        assertEquals(1, history.size());
        assertEquals(1, history.get(0).versionNumber());
        assertTrue(history.get(0).active());
        assertEquals("SEED", history.get(0).changeType());
    }

    @Test
    void registerPromptVersionUpdatesActiveTemplate() throws Exception {
        FakeAIPromptRegistryDAO dao = new FakeAIPromptRegistryDAO();
        AIPromptRegistryService service = new AIPromptRegistryService(dao);

        service.resolvePromptTemplate("sales_summary", "Version 1");
        AIPromptVersion updated = service.registerPromptVersion(
                "sales_summary",
                "Version 2",
                "Tune prompt wording",
                101);

        assertEquals(2, updated.versionNumber());
        assertEquals("Version 2", service.resolvePromptTemplate("sales_summary", "Fallback"));

        List<AIPromptVersion> history = service.getPromptVersionHistory("sales_summary", 10);
        assertEquals(2, history.size());
        assertEquals(2, history.get(0).versionNumber());
        assertTrue(history.get(0).active());
    }

    @Test
    void rollbackPromptVersionCreatesNewActiveVersionFromTarget() throws Exception {
        FakeAIPromptRegistryDAO dao = new FakeAIPromptRegistryDAO();
        AIPromptRegistryService service = new AIPromptRegistryService(dao);

        service.resolvePromptTemplate("subscription_multilingual_translation", "V1");
        service.registerPromptVersion("subscription_multilingual_translation", "V2", "iteration", 201);
        service.registerPromptVersion("subscription_multilingual_translation", "V3", "iteration", 202);

        AIPromptVersion rolledBack = service.rollbackPromptVersion(
                "subscription_multilingual_translation",
                1,
                "rollback to known-good",
                203);

        assertEquals(4, rolledBack.versionNumber());
        assertEquals("ROLLBACK", rolledBack.changeType());
        assertEquals(1, rolledBack.rolledBackFromVersion());
        assertEquals("V1", service.resolvePromptTemplate("subscription_multilingual_translation", "Fallback"));
    }

    private static class FakeAIPromptRegistryDAO extends AIPromptRegistryDAO {
        private final Map<String, List<AIPromptVersion>> versionsByKey = new HashMap<>();
        private long seq = 1L;

        @Override
        public Optional<AIPromptVersion> findActivePromptVersion(String promptKey) {
            return listUnsafe(promptKey).stream()
                    .filter(AIPromptVersion::active)
                    .max(Comparator.comparingInt(AIPromptVersion::versionNumber));
        }

        @Override
        public Optional<AIPromptVersion> findPromptVersion(String promptKey, int versionNumber) {
            return listUnsafe(promptKey).stream()
                    .filter(v -> v.versionNumber() == versionNumber)
                    .findFirst();
        }

        @Override
        public List<AIPromptVersion> listPromptVersions(String promptKey, int limit) {
            int safeLimit = Math.max(1, Math.min(500, limit <= 0 ? 50 : limit));
            return listUnsafe(promptKey).stream()
                    .sorted(Comparator.comparingInt(AIPromptVersion::versionNumber).reversed())
                    .limit(safeLimit)
                    .toList();
        }

        @Override
        public AIPromptVersion seedPromptIfMissing(String promptKey, String defaultTemplate) {
            Optional<AIPromptVersion> active = findActivePromptVersion(promptKey);
            if (active.isPresent()) {
                return active.get();
            }
            List<AIPromptVersion> rows = listUnsafe(promptKey);
            if (!rows.isEmpty()) {
                AIPromptVersion latest = rows.stream()
                        .max(Comparator.comparingInt(AIPromptVersion::versionNumber))
                        .orElse(rows.get(rows.size() - 1));
                deactivate(promptKey);
                AIPromptVersion activated = withActive(latest, true);
                upsert(promptKey, activated);
                return activated;
            }
            AIPromptVersion seeded = new AIPromptVersion(
                    seq++,
                    promptKey,
                    1,
                    defaultTemplate,
                    "SEED",
                    "Auto-seeded default prompt template.",
                    null,
                    true,
                    null,
                    "2026-02-23 00:00:00");
            upsert(promptKey, seeded);
            return seeded;
        }

        @Override
        public AIPromptVersion createPromptVersion(
                String promptKey,
                String templateText,
                String changeType,
                String changeNote,
                Integer changedByUserId,
                Integer rolledBackFromVersion) {
            int nextVersion = listUnsafe(promptKey).stream()
                    .mapToInt(AIPromptVersion::versionNumber)
                    .max()
                    .orElse(0) + 1;
            deactivate(promptKey);
            AIPromptVersion version = new AIPromptVersion(
                    seq++,
                    promptKey,
                    nextVersion,
                    templateText,
                    changeType,
                    changeNote,
                    rolledBackFromVersion,
                    true,
                    changedByUserId,
                    "2026-02-23 00:00:00");
            upsert(promptKey, version);
            return version;
        }

        private void deactivate(String promptKey) {
            List<AIPromptVersion> rows = listUnsafe(promptKey);
            List<AIPromptVersion> updated = new ArrayList<>();
            for (AIPromptVersion row : rows) {
                updated.add(withActive(row, false));
            }
            versionsByKey.put(promptKey, updated);
        }

        private void upsert(String promptKey, AIPromptVersion version) {
            List<AIPromptVersion> rows = listUnsafe(promptKey);
            rows.removeIf(v -> v.versionNumber() == version.versionNumber());
            rows.add(version);
            versionsByKey.put(promptKey, rows);
        }

        private AIPromptVersion withActive(AIPromptVersion source, boolean active) {
            return new AIPromptVersion(
                    source.promptVersionId(),
                    source.promptKey(),
                    source.versionNumber(),
                    source.templateText(),
                    source.changeType(),
                    source.changeNote(),
                    source.rolledBackFromVersion(),
                    active,
                    source.changedByUserId(),
                    source.createdAt());
        }

        private List<AIPromptVersion> listUnsafe(String promptKey) {
            List<AIPromptVersion> rows = versionsByKey.get(promptKey);
            if (rows == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(rows);
        }
    }
}
