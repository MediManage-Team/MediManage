package org.example.MediManage.service;

import org.example.MediManage.config.DatabaseConfig;
import org.example.MediManage.util.DatabaseUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DatabaseMaintenanceService {
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public record DatabaseHealthSnapshot(
            String databasePath,
            long fileSizeBytes,
            String journalMode,
            String integrityStatus,
            int usersCount,
            int medicinesCount,
            int billsCount,
            int activeBatchCount,
            int auditEventCount,
            String lastBackupAt) {
    }

    public record BackupHistoryEntry(
            int backupId,
            String backupPath,
            String backupType,
            long fileSizeBytes,
            String status,
            String notes,
            String createdAt) {
    }

    public File defaultBackupFile(File directory) {
        File safeDirectory = directory == null ? DatabaseConfig.getResolvedDatabaseFile().getParentFile() : directory;
        return new File(safeDirectory, "medimanage_backup_" + LocalDateTime.now().format(BACKUP_STAMP) + ".db");
    }

    public void createBackup(File destinationFile, Integer initiatedByUserId, String notes) throws SQLException, IOException {
        if (destinationFile == null) {
            throw new IOException("Choose a backup destination file.");
        }
        Path destination = destinationFile.toPath().toAbsolutePath();
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM INTO '" + escapeSqlitePath(destination.toString()) + "'");
        }

        long size = Files.exists(destination) ? Files.size(destination) : 0L;
        recordBackupHistory(destination.toString(), "MANUAL_BACKUP", size, "SUCCESS", initiatedByUserId, notes);
    }

    public void restoreBackup(File sourceFile, Integer initiatedByUserId, String notes) throws SQLException, IOException {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            throw new IOException("Choose a valid backup file.");
        }

        DatabaseConfig.ConnectionSettings sourceSettings = new DatabaseConfig.ConnectionSettings(
                DatabaseConfig.Backend.SQLITE,
                sourceFile.getAbsolutePath());
        DatabaseConfig.testConnection(sourceSettings);
        validateSchemaPresence(sourceSettings);

        File liveDatabase = DatabaseConfig.getResolvedDatabaseFile();
        if (liveDatabase.getAbsolutePath().equalsIgnoreCase(sourceFile.getAbsolutePath())) {
            throw new IOException("Backup source and active database are the same file.");
        }

        Path livePath = liveDatabase.toPath().toAbsolutePath();
        Path sourcePath = sourceFile.toPath().toAbsolutePath();
        if (livePath.getParent() != null) {
            Files.createDirectories(livePath.getParent());
        }

        File safetyBackup = defaultBackupFile(liveDatabase.getParentFile());
        if (liveDatabase.exists()) {
            Files.copy(livePath, safetyBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        deleteIfExists(livePath.resolveSibling(liveDatabase.getName() + "-wal"));
        deleteIfExists(livePath.resolveSibling(liveDatabase.getName() + "-shm"));
        Files.copy(sourcePath, livePath, StandardCopyOption.REPLACE_EXISTING);
        DatabaseUtil.initDB();

        long restoredSize = Files.exists(livePath) ? Files.size(livePath) : 0L;
        recordBackupHistory(livePath.toString(), "RESTORE", restoredSize, "SUCCESS", initiatedByUserId,
                (notes == null ? "" : notes) + " | source=" + sourceFile.getAbsolutePath()
                        + " | safetyBackup=" + safetyBackup.getAbsolutePath());
    }

    public DatabaseHealthSnapshot getHealthSnapshot() throws SQLException, IOException {
        File dbFile = DatabaseConfig.getResolvedDatabaseFile();
        long size = dbFile.exists() ? Files.size(dbFile.toPath()) : 0L;

        String journalMode = "unknown";
        String integrityStatus = "unknown";
        int usersCount = 0;
        int medicinesCount = 0;
        int billsCount = 0;
        int activeBatchCount = 0;
        int auditEventCount = 0;
        String lastBackupAt = "";

        try (Connection conn = DatabaseUtil.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    journalMode = rs.getString(1);
                }
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                if (rs.next()) {
                    integrityStatus = rs.getString(1);
                }
            }

            usersCount = countTable(conn, "users");
            medicinesCount = countTable(conn, "medicines");
            billsCount = countTable(conn, "bills");
            activeBatchCount = countWhere(conn, "inventory_batches", "available_quantity > 0");
            auditEventCount = countTable(conn, "audit_events");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT created_at FROM backup_history ORDER BY created_at DESC, backup_id DESC LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    lastBackupAt = rs.getString(1);
                }
            } catch (SQLException ignored) {
                lastBackupAt = "";
            }
        }

        return new DatabaseHealthSnapshot(
                dbFile.getAbsolutePath(),
                size,
                journalMode,
                integrityStatus,
                usersCount,
                medicinesCount,
                billsCount,
                activeBatchCount,
                auditEventCount,
                lastBackupAt);
    }

    public List<BackupHistoryEntry> getRecentBackups(int limit) throws SQLException {
        List<BackupHistoryEntry> rows = new ArrayList<>();
        String sql = """
                SELECT backup_id,
                       backup_path,
                       backup_type,
                       file_size_bytes,
                       status,
                       COALESCE(notes, '') AS notes,
                       created_at
                FROM backup_history
                ORDER BY created_at DESC, backup_id DESC
                LIMIT ?
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BackupHistoryEntry(
                            rs.getInt("backup_id"),
                            rs.getString("backup_path"),
                            rs.getString("backup_type"),
                            rs.getLong("file_size_bytes"),
                            rs.getString("status"),
                            rs.getString("notes"),
                            rs.getString("created_at")));
                }
            }
        }
        return rows;
    }

    private void validateSchemaPresence(DatabaseConfig.ConnectionSettings settings) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection(settings);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('users', 'medicines', 'bills')")) {
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                if (count < 3) {
                    throw new SQLException("Backup file does not look like a valid MediManage database.");
                }
            }
        }
    }

    private void recordBackupHistory(
            String backupPath,
            String backupType,
            long fileSizeBytes,
            String status,
            Integer initiatedByUserId,
            String notes) throws SQLException {
        String sql = """
                INSERT INTO backup_history (
                    backup_path,
                    backup_type,
                    file_size_bytes,
                    status,
                    initiated_by_user_id,
                    notes
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, backupPath);
            ps.setString(2, backupType);
            ps.setLong(3, Math.max(0L, fileSizeBytes));
            ps.setString(4, status);
            if (initiatedByUserId != null && initiatedByUserId > 0) {
                ps.setInt(5, initiatedByUserId);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setString(6, notes == null ? "" : notes.trim());
            ps.executeUpdate();
        }
    }

    private int countTable(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int countWhere(Connection conn, String tableName, String whereClause) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void deleteIfExists(Path path) throws IOException {
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    private String escapeSqlitePath(String path) {
        return path.replace("'", "''");
    }
}
