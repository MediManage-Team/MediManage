package org.example.MediManage.service;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.config.DatabaseConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseMigrationService {
    private record TablePlan(String name, String idColumn) {
    }

    public record MigrationResult(String backupPath, Map<String, Integer> migratedRowsByTable) {
    }

    private static final List<TablePlan> TABLES = List.of(
            new TablePlan("users", "user_id"),
            new TablePlan("medicines", "medicine_id"),
            new TablePlan("stock", "stock_id"),
            new TablePlan("customers", "customer_id"),
            new TablePlan("bills", "bill_id"),
            new TablePlan("bill_items", "item_id"),
            new TablePlan("expenses", "expense_id"),
            new TablePlan("prescriptions", "prescription_id"));

    public MigrationResult migrateSqliteToPostgres(
            DatabaseConfig.ConnectionSettings sqliteSettings,
            DatabaseConfig.ConnectionSettings postgresSettings) throws SQLException, IOException {
        validateSettings(sqliteSettings, postgresSettings);

        Path backupPath = backupSqliteFile(sqliteSettings.sqlitePath());
        DatabaseUtil.initDB(postgresSettings);

        Map<String, Integer> migratedRows = new LinkedHashMap<>();

        try (Connection source = DatabaseConfig.getConnection(sqliteSettings);
                Connection target = DatabaseConfig.getConnection(postgresSettings)) {
            target.setAutoCommit(false);
            try {
                truncateTargetTables(target);

                for (TablePlan table : TABLES) {
                    int copied = copyTable(source, target, table.name());
                    int sourceCount = countRows(source, table.name());
                    int targetCount = countRows(target, table.name());

                    if (copied != sourceCount || targetCount != sourceCount) {
                        throw new SQLException("Row-count verification failed for table '" + table.name()
                                + "' (source=" + sourceCount + ", copied=" + copied + ", target=" + targetCount + ")");
                    }
                    migratedRows.put(table.name(), copied);
                }

                alignIdentitySequences(target);
                target.commit();
            } catch (Exception migrationError) {
                target.rollback();
                if (migrationError instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Migration failed: " + migrationError.getMessage(), migrationError);
            } finally {
                target.setAutoCommit(true);
            }
        }

        return new MigrationResult(backupPath.toString(), migratedRows);
    }

    private void validateSettings(
            DatabaseConfig.ConnectionSettings sqliteSettings,
            DatabaseConfig.ConnectionSettings postgresSettings) throws SQLException {
        if (sqliteSettings.backend() != DatabaseConfig.Backend.SQLITE) {
            throw new SQLException("Source settings must use SQLITE backend.");
        }
        if (postgresSettings.backend() != DatabaseConfig.Backend.POSTGRESQL) {
            throw new SQLException("Target settings must use POSTGRESQL backend.");
        }
        if (sqliteSettings.sqlitePath() == null || sqliteSettings.sqlitePath().isBlank()) {
            throw new SQLException("SQLite source path is required.");
        }
    }

    private Path backupSqliteFile(String sqlitePath) throws IOException {
        Path source = Path.of(sqlitePath);
        if (!Files.exists(source)) {
            throw new IOException("SQLite database file not found at: " + source.toAbsolutePath());
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String fileName = source.getFileName().toString() + ".backup-" + timestamp;
        Path backup = source.resolveSibling(fileName);

        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private void truncateTargetTables(Connection target) throws SQLException {
        String sql = "TRUNCATE TABLE bill_items, bills, stock, prescriptions, expenses, customers, medicines, users RESTART IDENTITY CASCADE";
        try (Statement stmt = target.createStatement()) {
            stmt.execute(sql);
        }
    }

    private int copyTable(Connection source, Connection target, String tableName) throws SQLException {
        String selectSql = "SELECT * FROM " + tableName;
        try (PreparedStatement sourceStmt = source.prepareStatement(selectSql);
                ResultSet rs = sourceStmt.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int columns = md.getColumnCount();
            String insertSql = buildInsertSql(tableName, md);

            int count = 0;
            try (PreparedStatement targetStmt = target.prepareStatement(insertSql)) {
                while (rs.next()) {
                    for (int i = 1; i <= columns; i++) {
                        targetStmt.setObject(i, rs.getObject(i));
                    }
                    targetStmt.addBatch();
                    count++;
                    if (count % 500 == 0) {
                        targetStmt.executeBatch();
                    }
                }
                targetStmt.executeBatch();
            }
            return count;
        }
    }

    private String buildInsertSql(String tableName, ResultSetMetaData md) throws SQLException {
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (i > 1) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(md.getColumnName(i));
            placeholders.append('?');
        }
        return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    private int countRows(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void alignIdentitySequences(Connection target) throws SQLException {
        for (TablePlan table : TABLES) {
            String sequenceName = lookupSequence(target, table.name(), table.idColumn());
            if (sequenceName == null || sequenceName.isBlank()) {
                continue;
            }
            String safeSequence = sequenceName.replace("'", "''");
            String sql = "SELECT setval('" + safeSequence + "', COALESCE((SELECT MAX(" + table.idColumn()
                    + ") FROM " + table.name() + "), 1), true)";
            try (Statement stmt = target.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    private String lookupSequence(Connection target, String tableName, String idColumn) throws SQLException {
        String sql = "SELECT pg_get_serial_sequence(?, ?)";
        try (PreparedStatement ps = target.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, idColumn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }
}
