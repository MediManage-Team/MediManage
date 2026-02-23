package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.SubscriptionAIDecisionLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionAIDecisionLogDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-subscription-ai-decision-log-tests-");
        testDbPath = tempDir.resolve("medimanage-subscription-ai-decision-log.db");
        System.setProperty(DB_PATH_PROPERTY, testDbPath.toString());
        DatabaseUtil.initDB();
    }

    @AfterAll
    static void cleanupDb() {
        System.clearProperty(DB_PATH_PROPERTY);
        if (testDbPath == null) {
            return;
        }
        String baseName = testDbPath.getFileName().toString();
        tryDelete(testDbPath.resolveSibling(baseName + "-shm"));
        tryDelete(testDbPath.resolveSibling(baseName + "-wal"));
        tryDelete(testDbPath);
        Path parent = testDbPath.getParent();
        if (parent != null) {
            tryDelete(parent);
        }
    }

    @Test
    void appendAndReadRecentDecisionLogs() throws Exception {
        SubscriptionAIDecisionLogDAO dao = new SubscriptionAIDecisionLogDAO();
        long insertedId = dao.appendDecisionLog(new SubscriptionAIDecisionLog(
                0L,
                "PLAN_RECOMMENDATION",
                "CUSTOMER",
                "9001",
                "PLAN_RECO_TOP_POSITIVE_BENEFIT",
                "Top recommendation returned positive monthly benefit.",
                "{\"customer_id\":9001,\"score\":87.5}",
                "SubscriptionPlanRecommendationEngine",
                "v1",
                null,
                null,
                null,
                null));
        assertTrue(insertedId > 0);

        List<SubscriptionAIDecisionLog> rows = dao.getRecentDecisionLogs(10);
        assertTrue(rows.size() >= 1);
        SubscriptionAIDecisionLog latest = rows.get(0);
        assertEquals("PLAN_RECOMMENDATION", latest.decisionType());
        assertEquals("PLAN_RECO_TOP_POSITIVE_BENEFIT", latest.reasonCode());
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // best effort cleanup
        }
    }
}
