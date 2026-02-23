package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsReportDispatchDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-dispatch-schedule-tests-");
        testDbPath = tempDir.resolve("medimanage-dispatch-schedule.db");
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
    void dueSchedulesCanBeCreatedFetchedAndMarkedSuccessful() {
        AnalyticsReportDispatchDAO dao = new AnalyticsReportDispatchDAO();
        LocalDateTime now = LocalDateTime.now();
        int scheduleId = dao.createSchedule(
                "EMAIL",
                "ops@example.com",
                "PDF",
                "WEEKLY",
                "UTC",
                LocalDate.now().minusDays(7),
                LocalDate.now(),
                "All",
                null,
                null,
                now.minusMinutes(2));

        List<AnalyticsReportDispatchDAO.DispatchScheduleRow> dueRows = dao.getDueSchedules(now, 20);
        assertTrue(dueRows.stream().anyMatch(row -> row.scheduleId() == scheduleId));

        LocalDateTime nextRun = now.plusWeeks(1);
        assertTrue(dao.markRunSuccess(scheduleId, now, nextRun));

        List<AnalyticsReportDispatchDAO.DispatchScheduleRow> dueAfterSuccess = dao.getDueSchedules(now, 20);
        assertFalse(dueAfterSuccess.stream().anyMatch(row -> row.scheduleId() == scheduleId));

        AnalyticsReportDispatchDAO.DispatchScheduleRow active = dao.getActiveSchedules(20).stream()
                .filter(row -> row.scheduleId() == scheduleId)
                .findFirst()
                .orElseThrow();
        assertEquals("SUCCESS", active.lastStatus());
        assertNotNull(active.nextRunAt());
    }

    @Test
    void markRunFailureStoresFailureStatusAndError() {
        AnalyticsReportDispatchDAO dao = new AnalyticsReportDispatchDAO();
        LocalDateTime now = LocalDateTime.now();
        int scheduleId = dao.createSchedule(
                "WHATSAPP",
                "+919999888777",
                "CSV",
                "DAILY",
                "UTC",
                null,
                null,
                null,
                null,
                null,
                now.minusMinutes(1));

        LocalDateTime nextRun = now.plusDays(1);
        assertTrue(dao.markRunFailure(scheduleId, now, nextRun, "gateway timeout"));

        AnalyticsReportDispatchDAO.DispatchScheduleRow row = dao.getActiveSchedules(20).stream()
                .filter(r -> r.scheduleId() == scheduleId)
                .findFirst()
                .orElseThrow();
        assertEquals("FAILED", row.lastStatus());
        assertTrue(row.lastError() != null && row.lastError().contains("gateway"));
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // best effort
        }
    }
}
