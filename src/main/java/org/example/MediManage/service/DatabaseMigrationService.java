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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseMigrationService {

    public record MigrationResult(String backupPath, Map<String, Integer> migratedRowsByTable) {
    }

    // All tables in FK-safe insertion order (parents before children)
    private static final List<String> TABLES = List.of(
            "users",
            "suppliers",
            "medicines",
            "stock",
            "customers",
            "doctors",
            "bills",
            "bill_items",
            "expenses",
            "prescriptions",
            "locations",
            "location_stock",
            "stock_transfers",
            "purchase_orders",
            "purchase_order_items",
            "employee_attendance",
            "held_orders",
            "receipt_settings",
            "payment_splits",
            "expiry_alerts",
            "inventory_adjustments",
            "medication_interactions",
            "notification_logs",
            "analytics_report_dispatch_schedules",
            "anomaly_action_tracker",
            "ai_prompt_registry");

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
                // Disable FK constraints and triggers during bulk data copy
                try (Statement stmt = target.createStatement()) {
                    stmt.execute("SET session_replication_role = 'replica'");
                }

                truncateTargetTables(target);

                for (String tableName : TABLES) {
                    if (!tableExists(source, tableName)) {
                        System.out.println("ℹ️ Skipping table '" + tableName + "' — not in source.");
                        continue;
                    }
                    if (!tableExists(target, tableName)) {
                        System.out.println("ℹ️ Skipping table '" + tableName + "' — not in target.");
                        continue;
                    }
                    int copied = copyTable(source, target, tableName);
                    int sourceCount = countRows(source, tableName);
                    int targetCount = countRows(target, tableName);

                    if (targetCount != copied) {
                        System.err.println("⚠️ Row-count mismatch for '" + tableName
                                + "' (source=" + sourceCount + ", copied=" + copied + ", target=" + targetCount + ")");
                    }
                    System.out.println("✅ Copied " + copied + "/" + sourceCount + " rows for '" + tableName + "'");
                    migratedRows.put(tableName, copied);
                }

                // Re-enable FK constraints and triggers
                try (Statement stmt = target.createStatement()) {
                    stmt.execute("SET session_replication_role = 'origin'");
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
                // Always restore default replication role
                try (Statement stmt = target.createStatement()) {
                    stmt.execute("SET session_replication_role = 'origin'");
                } catch (SQLException ignored) {
                }
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
        // Truncate in reverse order (children before parents) to respect FK constraints
        List<String> reverseTableNames = new java.util.ArrayList<>(TABLES);
        java.util.Collections.reverse(reverseTableNames);

        try (Statement stmt = target.createStatement()) {
            for (String tableName : reverseTableNames) {
                if (!tableExists(target, tableName)) {
                    continue;
                }
                // Use SAVEPOINT so a failure doesn't abort the whole transaction
                java.sql.Savepoint sp = target.setSavepoint("trunc_" + tableName);
                try {
                    stmt.execute("TRUNCATE TABLE \"" + tableName + "\" RESTART IDENTITY CASCADE");
                } catch (SQLException e) {
                    target.rollback(sp);
                    System.err.println("⚠️ Could not truncate '" + tableName + "': " + e.getMessage());
                } finally {
                    try {
                        target.releaseSavepoint(sp);
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
    }

    private int copyTable(Connection source, Connection target, String tableName) throws SQLException {
        // Find columns common to both source and target to handle schema differences
        List<String> commonColumns = getCommonColumns(source, target, tableName);
        if (commonColumns.isEmpty()) {
            System.err.println("⚠️ No common columns found for table '" + tableName + "' — skipping.");
            return 0;
        }

        // Get target column SQL types for proper type conversion
        Map<String, Integer> targetColumnTypes = getColumnTypes(target, tableName);

        String columnList = String.join(", ", commonColumns);
        String selectSql = "SELECT " + columnList + " FROM " + tableName;
        String insertSql = "INSERT INTO \"" + tableName + "\" (" + columnList + ") VALUES ("
                + commonColumns.stream().map(c -> "?").collect(java.util.stream.Collectors.joining(", ")) + ")";

        try (PreparedStatement sourceStmt = source.prepareStatement(selectSql);
                ResultSet rs = sourceStmt.executeQuery()) {
            int columns = commonColumns.size();
            int count = 0;
            try (PreparedStatement targetStmt = target.prepareStatement(insertSql)) {
                while (rs.next()) {
                    for (int i = 1; i <= columns; i++) {
                        Object value = rs.getObject(i);
                        String colName = commonColumns.get(i - 1);
                        int targetType = targetColumnTypes.getOrDefault(colName, java.sql.Types.OTHER);
                        targetStmt.setObject(i, convertValue(value, targetType));
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

    /**
     * Converts a SQLite value to the appropriate Java type for the PostgreSQL
     * target column.
     * SQLite stores everything as TEXT/INTEGER/REAL — PostgreSQL needs proper
     * types.
     */
    private Object convertValue(Object value, int targetSqlType) {
        if (value == null) {
            return null;
        }

        // SQLite TEXT → PostgreSQL typed columns
        if (value instanceof String strVal) {
            if (strVal.isEmpty()) {
                // Empty string → null for numeric/date types
                if (targetSqlType != java.sql.Types.VARCHAR && targetSqlType != java.sql.Types.CHAR
                        && targetSqlType != java.sql.Types.LONGVARCHAR) {
                    return null;
                }
                return strVal;
            }

            // TEXT → TIMESTAMP
            if (targetSqlType == java.sql.Types.TIMESTAMP || targetSqlType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
                try {
                    return java.sql.Timestamp.valueOf(strVal);
                } catch (IllegalArgumentException e) {
                    try {
                        return java.sql.Date.valueOf(strVal);
                    } catch (IllegalArgumentException e2) {
                        return strVal;
                    }
                }
            }
            // TEXT → DATE
            if (targetSqlType == java.sql.Types.DATE) {
                try {
                    return java.sql.Date.valueOf(strVal);
                } catch (IllegalArgumentException e) {
                    return strVal;
                }
            }
            // TEXT → BOOLEAN
            if (targetSqlType == java.sql.Types.BOOLEAN || targetSqlType == java.sql.Types.BIT) {
                return "1".equals(strVal) || "true".equalsIgnoreCase(strVal);
            }
            // TEXT → INTEGER / SMALLINT
            if (targetSqlType == java.sql.Types.INTEGER || targetSqlType == java.sql.Types.SMALLINT
                    || targetSqlType == java.sql.Types.TINYINT) {
                try {
                    return Integer.parseInt(strVal.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            // TEXT → BIGINT
            if (targetSqlType == java.sql.Types.BIGINT) {
                try {
                    return Long.parseLong(strVal.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            // TEXT → NUMERIC / DECIMAL
            if (targetSqlType == java.sql.Types.NUMERIC || targetSqlType == java.sql.Types.DECIMAL) {
                try {
                    return new java.math.BigDecimal(strVal.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            // TEXT → FLOAT / DOUBLE / REAL
            if (targetSqlType == java.sql.Types.FLOAT || targetSqlType == java.sql.Types.DOUBLE
                    || targetSqlType == java.sql.Types.REAL) {
                try {
                    return Double.parseDouble(strVal.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        // SQLite INTEGER → PostgreSQL BOOLEAN
        if (value instanceof Number numVal
                && (targetSqlType == java.sql.Types.BOOLEAN || targetSqlType == java.sql.Types.BIT)) {
            return numVal.intValue() != 0;
        }

        return value;
    }

    /**
     * Returns a map of column_name (lowercase) → SQL type code for the given table.
     */
    private Map<String, Integer> getColumnTypes(Connection conn, String tableName) throws SQLException {
        Map<String, Integer> types = new java.util.HashMap<>();
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                types.put(rs.getString("COLUMN_NAME").toLowerCase(), rs.getInt("DATA_TYPE"));
            }
        }
        if (types.isEmpty()) {
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toLowerCase(), null)) {
                while (rs.next()) {
                    types.put(rs.getString("COLUMN_NAME").toLowerCase(), rs.getInt("DATA_TYPE"));
                }
            }
        }
        return types;
    }

    /**
     * Returns the list of column names that exist in both source and target for the
     * given table.
     */
    private List<String> getCommonColumns(Connection source, Connection target, String tableName) throws SQLException {
        java.util.Set<String> sourceColumns = getColumnNames(source, tableName);
        java.util.Set<String> targetColumns = getColumnNames(target, tableName);
        // Preserve source column order, but only include those that exist in target
        return sourceColumns.stream()
                .filter(targetColumns::contains)
                .collect(java.util.stream.Collectors.toList());
    }

    private java.util.Set<String> getColumnNames(Connection conn, String tableName) throws SQLException {
        java.util.Set<String> columns = new java.util.LinkedHashSet<>();
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        // Also try lowercase table name (PostgreSQL stores identifiers lowercase)
        if (columns.isEmpty()) {
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toLowerCase(), null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        return columns;
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

    private boolean tableExists(Connection conn, String tableName) {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            if (rs.next())
                return true;
        } catch (SQLException ignored) {
        }
        // Try lowercase (PostgreSQL stores names lowercase)
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toLowerCase(), null)) {
            if (rs.next())
                return true;
        } catch (SQLException ignored) {
        }
        return false;
    }

    /**
     * Auto-detects PK columns from PostgreSQL metadata and aligns sequences.
     * No hardcoded column names needed — reads them directly from the database.
     */
    private void alignIdentitySequences(Connection target) throws SQLException {
        for (String tableName : TABLES) {
            if (!tableExists(target, tableName)) {
                continue;
            }
            // Auto-detect PK column from database metadata
            String pkColumn = getPrimaryKeyColumn(target, tableName);
            if (pkColumn == null) {
                continue; // No primary key for this table
            }
            String sequenceName = lookupSequence(target, tableName, pkColumn);
            if (sequenceName == null || sequenceName.isBlank()) {
                continue;
            }
            String safeSequence = sequenceName.replace("'", "''");
            String sql = "SELECT setval('" + safeSequence + "', COALESCE((SELECT MAX(" + pkColumn
                    + ") FROM " + tableName + "), 1), true)";
            try (Statement stmt = target.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * Auto-detects the single-column primary key for a table from database
     * metadata.
     * Returns null for composite PKs or tables without PKs.
     */
    private String getPrimaryKeyColumn(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, tableName)) {
            List<String> pkColumns = new java.util.ArrayList<>();
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
            if (pkColumns.size() == 1) {
                return pkColumns.get(0);
            }
        }
        // Try lowercase table name (PostgreSQL stores identifiers lowercase)
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, tableName.toLowerCase())) {
            List<String> pkColumns = new java.util.ArrayList<>();
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
            if (pkColumns.size() == 1) {
                return pkColumns.get(0);
            }
        }
        return null;
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
