package org.example.MediManage.util;

import org.example.MediManage.config.DatabaseConfig;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseUtil {
    private static final Logger LOGGER = Logger.getLogger(DatabaseUtil.class.getName());
    private static final Pattern ALTER_ADD_COLUMN_PATTERN = Pattern.compile(
            "(?is)^ALTER\\s+TABLE\\s+([`\"\\[]?[A-Za-z_][A-Za-z0-9_]*[`\"\\]]?)\\s+ADD\\s+COLUMN\\s+([`\"\\[]?[A-Za-z_][A-Za-z0-9_]*[`\"\\]]?).*");

    public static Connection getConnection() throws SQLException {
        return DatabaseConfig.getConnection();
    }

    public static void initDB() throws SQLException {
        try (Connection conn = getConnection()) {
            // Ensure SQLite FK checks are on for each new connection.
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            LOGGER.info("Connected to database successfully.");

            runSchema(conn);
            reconcileInventoryBatches(conn);
            purgeLegacyDemoAdminIfPresent(conn);

            seedMessageTemplatesIfNeeded(conn);
        }
    }

    public static void initDB(DatabaseConfig.ConnectionSettings settings) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection(settings)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            LOGGER.info("Connected to database successfully.");
            runSchema(conn);
            reconcileInventoryBatches(conn);
            purgeLegacyDemoAdminIfPresent(conn);
            seedMessageTemplatesIfNeeded(conn);
        }
    }

    private static void runSchema(Connection conn) throws SQLException {
        String schemaResource = resolveSchemaResource(conn);
        List<String> statements = loadSqlStatements(schemaResource);
        List<String> deferredStatements = new ArrayList<>();
        boolean previousAutoCommit = conn.getAutoCommit();

        LOGGER.info("Applying database schema migrations.");
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String normalized = stripLeadingSqlComments(sql).trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                if (shouldSkipAlterAddColumn(conn, normalized)) {
                    continue;
                }
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    if (shouldDeferStatement(normalized, e)) {
                        deferredStatements.add(sql);
                        continue;
                    }
                    throw new SQLException("Schema initialization failed while executing: " + previewSql(normalized), e);
                }
            }

            for (String sql : deferredStatements) {
                String normalized = stripLeadingSqlComments(sql).trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    throw new SQLException("Schema initialization failed while executing: " + previewSql(normalized), e);
                }
            }
            conn.commit();
            LOGGER.info("Schema initialized successfully.");
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static void seedMessageTemplatesIfNeeded(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM message_templates")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Templates already seeded
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not check message_templates: {0}", e.getMessage());
            return;
        }

        LOGGER.info("Seeding default message templates.");
        org.example.MediManage.dao.MessageTemplateDAO dao = new org.example.MediManage.dao.MessageTemplateDAO();
        try {
            dao.save(new org.example.MediManage.model.MessageTemplate(0, org.example.MediManage.dao.MessageTemplateDAO.KEY_WHATSAPP_INVOICE, null, dao.getDefaultBody(org.example.MediManage.dao.MessageTemplateDAO.KEY_WHATSAPP_INVOICE)));
            dao.save(new org.example.MediManage.model.MessageTemplate(0, org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT, dao.getDefaultBody(org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT), dao.getDefaultBody(org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT)));
            dao.save(new org.example.MediManage.model.MessageTemplate(0, org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY, null, dao.getDefaultBody(org.example.MediManage.dao.MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY)));
            LOGGER.info("Default message templates seeded.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to seed templates: {0}", e.getMessage());
        }
    }

    public static void seedDemoData() throws SQLException {
        try (Connection conn = getConnection()) {
            LOGGER.info("Seeding demo data explicitly.");
            runSqlResource(conn, "/db/seed_data.sql");
            runSqlResource(conn, "/db/seed_dashboard.sql");
        }
    }

    public static boolean requiresAdminBootstrap() throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE UPPER(role) = 'ADMIN'");
             ResultSet rs = stmt.executeQuery()) {
            return !rs.next() || rs.getInt(1) == 0;
        }
    }

    public static boolean hasLegacyDemoAdminCredential() throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM users
                WHERE UPPER(role) = 'ADMIN'
                  AND (
                    (username = '1' AND password = '1')
                    OR (LOWER(username) = 'admin' AND LOWER(password) = 'admin')
                  )
                """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private static void purgeLegacyDemoAdminIfPresent(Connection conn) throws SQLException {
        String sql = """
                DELETE FROM users
                WHERE UPPER(role) = 'ADMIN'
                  AND (
                    (username = '1' AND password = '1')
                    OR (LOWER(username) = 'admin' AND LOWER(password) = 'admin')
                  )
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                LOGGER.warning("Removed legacy demo admin credential(s) from the database.");
            }
        }
    }

    private static void reconcileInventoryBatches(Connection conn) {
        String query = """
                SELECT m.medicine_id,
                       COALESCE(s.quantity, 0) AS stock_qty,
                       COALESCE(m.expiry_date, '') AS expiry_date,
                       COALESCE(m.purchase_price, 0.0) AS purchase_price,
                       COALESCE(m.price, 0.0) AS selling_price,
                       COALESCE(m.supplier_id, 0) AS supplier_id,
                       COALESCE((
                           SELECT SUM(available_quantity)
                           FROM inventory_batches ib
                           WHERE ib.medicine_id = m.medicine_id
                       ), 0) AS batch_qty
                FROM medicines m
                LEFT JOIN stock s ON s.medicine_id = m.medicine_id
                WHERE m.active = 1
                """;
        String insert = """
                INSERT INTO inventory_batches (
                    medicine_id,
                    batch_number,
                    batch_barcode,
                    expiry_date,
                    purchase_date,
                    unit_cost,
                    selling_price,
                    initial_quantity,
                    available_quantity,
                    supplier_id
                )
                VALUES (?, ?, ?, ?, DATE('now'), ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement queryPs = conn.prepareStatement(query);
             ResultSet rs = queryPs.executeQuery();
             PreparedStatement insertPs = conn.prepareStatement(insert)) {
            while (rs.next()) {
                int medicineId = rs.getInt("medicine_id");
                int stockQty = rs.getInt("stock_qty");
                int batchQty = rs.getInt("batch_qty");
                int gap = stockQty - batchQty;
                if (gap <= 0) {
                    continue;
                }

                insertPs.setInt(1, medicineId);
                String legacyBatchNumber = "LEGACY-" + medicineId + "-" + System.currentTimeMillis();
                insertPs.setString(2, legacyBatchNumber);
                insertPs.setString(3, "BT-" + medicineId + "-" + legacyBatchNumber.replaceAll("[^A-Za-z0-9]+", ""));
                insertPs.setString(4, rs.getString("expiry_date"));
                insertPs.setDouble(5, rs.getDouble("purchase_price"));
                insertPs.setDouble(6, rs.getDouble("selling_price"));
                insertPs.setInt(7, gap);
                insertPs.setInt(8, gap);
                int supplierId = rs.getInt("supplier_id");
                if (supplierId > 0) {
                    insertPs.setInt(9, supplierId);
                } else {
                    insertPs.setNull(9, java.sql.Types.INTEGER);
                }
                insertPs.addBatch();
            }
            insertPs.executeBatch();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to reconcile inventory batches: {0}", e.getMessage());
        }
    }

    private static List<String> loadSqlStatements(String resourcePath) throws SQLException {
        List<String> statements = new ArrayList<>();
        try (InputStream is = DatabaseUtil.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new SQLException("Critical schema resource missing: " + resourcePath);
            }
            try (java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter(";");
                while (scanner.hasNext()) {
                    String sql = scanner.next().trim();
                    if (!sql.isEmpty()) {
                        statements.add(sql);
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new SQLException("Failed to read SQL resource " + resourcePath, e);
        }
        return statements;
    }

    private static String previewSql(String sql) {
        String collapsed = sql.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= 120) {
            return collapsed;
        }
        return collapsed.substring(0, 117) + "...";
    }

    /**
     * Executes all SQL statements from a classpath resource file.
     */
    private static void runSqlResource(Connection conn, String resourcePath) {
        try (InputStream is = DatabaseUtil.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.log(Level.WARNING, "SQL resource not found: {0}", resourcePath);
                return;
            }
            try (java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter(";");
                try (Statement stmt = conn.createStatement()) {
                    while (scanner.hasNext()) {
                        String sql = scanner.next().trim();
                        if (!sql.isEmpty() && !sql.startsWith("--")) {
                            try {
                                stmt.execute(sql);
                            } catch (SQLException e) {
                                LOGGER.log(Level.WARNING, "SQL resource warning for {0}: {1}",
                                        new Object[] { resourcePath, e.getMessage() });
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException | SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to execute SQL resource " + resourcePath, e);
        }
    }

    private static String resolveSchemaResource(Connection conn) throws SQLException {
        return "/db/schema.sql";
    }

    private static boolean shouldSkipAlterAddColumn(Connection conn, String sql) throws SQLException {
        String normalized = stripLeadingSqlComments(sql).trim();
        Matcher matcher = ALTER_ADD_COLUMN_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return false;
        }

        String tableName = sanitizeIdentifier(matcher.group(1));
        String columnName = sanitizeIdentifier(matcher.group(2));
        if (tableName.isEmpty() || columnName.isEmpty()) {
            return false;
        }

        if (columnExists(conn, tableName, columnName)) {
            LOGGER.log(Level.FINE, "Skipped migration because column {0} already exists on {1}.",
                    new Object[] { columnName, tableName });
            return true;
        }
        return false;
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, null)) {
            while (rs.next()) {
                String existing = rs.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(existing)) {
                    return true;
                }
            }
        }
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), "public", tableName, null)) {
            while (rs.next()) {
                String existing = rs.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(existing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldDeferStatement(String normalizedSql, SQLException error) {
        if (normalizedSql == null) {
            return false;
        }

        String trimmed = normalizedSql.trim().toUpperCase(java.util.Locale.ROOT);
        if (!trimmed.startsWith("CREATE INDEX")) {
            return false;
        }

        String message = error.getMessage();
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase(java.util.Locale.ROOT);
        return lowerMessage.contains("no such column") || lowerMessage.contains("no such table");
    }

    private static String stripLeadingSqlComments(String sql) {
        StringBuilder cleaned = new StringBuilder(sql.length());
        boolean started = false;

        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (!started && (trimmed.isEmpty() || trimmed.startsWith("--"))) {
                continue;
            }
            started = true;
            cleaned.append(line).append('\n');
        }
        return cleaned.toString();
    }

    private static String sanitizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("`") && value.endsWith("`"))
                    || (value.startsWith("[") && value.endsWith("]"))) {
                value = value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Wipes all operational database tables, retaining only the Users and Message Templates.
     * This is intended for customers to clear out the demo database before starting real operations.
     */
    public static void clearDemoData() throws SQLException {
        String[] tablesToClear = {
            "inventory_adjustments",
            "expenses",
            "held_order_items",
            "held_orders",
            "order_items",
            "orders",
            "bill_items",
            "bills",
            "suppliers",
            "stock",
            "medicines",
            "customers"
        };
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Temporarily disable FK checks to allow bulk deletion in any order
                stmt.execute("PRAGMA foreign_keys = OFF;");
                
                for (String table : tablesToClear) {
                    stmt.execute("DELETE FROM " + table + ";");
                    // Reset sqlite_sequence for this table if it exists
                    stmt.execute("DELETE FROM sqlite_sequence WHERE name='" + table + "';");
                }
                
                stmt.execute("PRAGMA foreign_keys = ON;");
                conn.commit();
                LOGGER.info("Successfully cleared demo data.");
            } catch (SQLException e) {
                conn.rollback();
                throw new SQLException("Failed to clear demo data. Transaction rolled back.", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
