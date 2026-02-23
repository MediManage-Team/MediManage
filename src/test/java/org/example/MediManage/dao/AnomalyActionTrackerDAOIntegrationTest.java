package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.util.WeeklyAnomalyAlertEvaluator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyActionTrackerDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-action-tracker-tests-");
        testDbPath = tempDir.resolve("medimanage-action-tracker.db");
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
    void syncAndGetWeeklyActionsCreatesRowsWithOwnerDueAndStatus() throws Exception {
        AnomalyActionTrackerDAO dao = new AnomalyActionTrackerDAO();
        int ownerUserId = insertUser("tracker_owner_" + System.nanoTime(), "MANAGER");
        LocalDate referenceDate = LocalDate.now();
        String timezone = "Asia/Kolkata";

        List<WeeklyAnomalyAlertEvaluator.AnomalyAlert> alerts = List.of(
                new WeeklyAnomalyAlertEvaluator.AnomalyAlert(
                        "Expiry Spike",
                        "HIGH",
                        "Current week expiries: 8 | Previous week: 3",
                        "Spike >= 50%",
                        "Expiry count increased sharply."),
                new WeeklyAnomalyAlertEvaluator.AnomalyAlert(
                        "Stock-Out Spike",
                        "MEDIUM",
                        "Out-of-stock SKUs: 12",
                        "SKUs >= 10",
                        "Stock-outs crossed threshold."));

        List<AnomalyActionTrackerDAO.ActionTrackerRow> rows = dao.syncAndGetWeeklyActions(
                referenceDate,
                timezone,
                alerts,
                ownerUserId);

        assertEquals(2, rows.size());
        AnomalyActionTrackerDAO.ActionTrackerRow expiryRow = rows.stream()
                .filter(row -> "Expiry Spike".equals(row.alertType()))
                .findFirst()
                .orElseThrow();
        assertEquals("HIGH", expiryRow.severity());
        assertEquals(ownerUserId, expiryRow.ownerUserId());
        assertNotNull(expiryRow.ownerUsername());
        assertEquals("OPEN", expiryRow.closureStatus());
        assertNotNull(expiryRow.dueDate());
    }

    @Test
    void updateActionPersistsClosureStatusAndDueDate() throws Exception {
        AnomalyActionTrackerDAO dao = new AnomalyActionTrackerDAO();
        int ownerUserId = insertUser("tracker_update_" + System.nanoTime(), "ADMIN");
        LocalDate referenceDate = LocalDate.now();
        String timezone = "Asia/Kolkata";

        List<AnomalyActionTrackerDAO.ActionTrackerRow> synced = dao.syncAndGetWeeklyActions(
                referenceDate,
                timezone,
                List.of(new WeeklyAnomalyAlertEvaluator.AnomalyAlert(
                        "Discount Leakage",
                        "HIGH",
                        "Leakage: 12.5%",
                        "Leakage >= 8%",
                        "Leakage above threshold.")),
                ownerUserId);
        assertTrue(synced.size() >= 1);
        int actionId = synced.stream()
                .filter(row -> "Discount Leakage".equals(row.alertType()))
                .findFirst()
                .orElseThrow()
                .actionId();
        LocalDate dueDate = referenceDate.plusDays(3);

        boolean updated = dao.updateAction(actionId, ownerUserId, dueDate, "CLOSED");
        assertTrue(updated);

        List<AnomalyActionTrackerDAO.ActionTrackerRow> refreshed =
                dao.getWeeklyActions(referenceDate, timezone, 50);
        AnomalyActionTrackerDAO.ActionTrackerRow updatedRow = refreshed.stream()
                .filter(row -> row.actionId() == actionId)
                .findFirst()
                .orElseThrow();

        assertEquals("CLOSED", updatedRow.closureStatus());
        assertEquals(dueDate.toString(), updatedRow.dueDate());
        assertNotNull(updatedRow.closedAt());
    }

    private static int insertUser(String username, String role) throws Exception {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "password");
            ps.setString(3, role);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert user");
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best effort cleanup.
        }
    }
}
