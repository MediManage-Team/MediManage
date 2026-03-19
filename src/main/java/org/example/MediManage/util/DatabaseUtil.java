package org.example.MediManage.util;

import org.example.MediManage.config.DatabaseConfig;
import org.example.MediManage.dao.MessageTemplateDAO;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.security.PasswordHasher;
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
            dropLegacyPrescriptionTableIfPresent(conn);
            dropLegacyLocationTablesIfPresent(conn);
            migrateBillsTableToRemoveLocationColumn(conn);
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
            dropLegacyPrescriptionTableIfPresent(conn);
            dropLegacyLocationTablesIfPresent(conn);
            migrateBillsTableToRemoveLocationColumn(conn);
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
        MessageTemplateDAO dao = new MessageTemplateDAO();
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
        try {
            insertMessageTemplate(conn, MessageTemplateDAO.KEY_WHATSAPP_INVOICE, null,
                    dao.getDefaultBody(MessageTemplateDAO.KEY_WHATSAPP_INVOICE));
            insertMessageTemplate(conn, MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT,
                    dao.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT),
                    dao.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT));
            insertMessageTemplate(conn, MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY, null,
                    dao.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY));
            LOGGER.info("Default message templates seeded.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to seed templates: {0}", e.getMessage());
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

    private static void dropLegacyPrescriptionTableIfPresent(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS prescriptions");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to remove legacy prescriptions table: {0}", e.getMessage());
        }
    }

    private static void dropLegacyLocationTablesIfPresent(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS stock_transfers");
            stmt.execute("DROP TABLE IF EXISTS location_stock");
            stmt.execute("DROP TABLE IF EXISTS locations");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to remove legacy location tables: {0}", e.getMessage());
        }
    }

    private static void migrateBillsTableToRemoveLocationColumn(Connection conn) {
        try {
            if (!columnExists(conn, "bills", "location_id")) {
                return;
            }

            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = OFF");
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS bills_migrated (
                            bill_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            customer_id INTEGER,
                            user_id INTEGER,
                            total_amount REAL,
                            bill_date TEXT DEFAULT CURRENT_TIMESTAMP,
                            payment_mode TEXT DEFAULT 'CASH',
                            ai_care_protocol TEXT,
                            subscription_enrollment_id INTEGER,
                            subscription_plan_id INTEGER,
                            subscription_discount_percent REAL DEFAULT 0.0,
                            subscription_savings_amount REAL DEFAULT 0.0,
                            subscription_approval_reference TEXT,
                            FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
                            FOREIGN KEY (user_id) REFERENCES users(user_id),
                            FOREIGN KEY (subscription_enrollment_id) REFERENCES subscription_enrollments(enrollment_id),
                            FOREIGN KEY (subscription_plan_id) REFERENCES subscription_plans(plan_id)
                        )
                        """);
                stmt.execute("""
                        INSERT INTO bills_migrated (
                            bill_id,
                            customer_id,
                            user_id,
                            total_amount,
                            bill_date,
                            payment_mode,
                            ai_care_protocol,
                            subscription_enrollment_id,
                            subscription_plan_id,
                            subscription_discount_percent,
                            subscription_savings_amount,
                            subscription_approval_reference
                        )
                        SELECT
                            bill_id,
                            customer_id,
                            user_id,
                            total_amount,
                            bill_date,
                            payment_mode,
                            ai_care_protocol,
                            subscription_enrollment_id,
                            subscription_plan_id,
                            subscription_discount_percent,
                            subscription_savings_amount,
                            subscription_approval_reference
                        FROM bills
                        """);
                stmt.execute("DROP TABLE bills");
                stmt.execute("ALTER TABLE bills_migrated RENAME TO bills");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_bills_date ON bills(bill_date)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_bills_customer ON bills(customer_id)");
                stmt.execute("PRAGMA foreign_keys = ON");
                conn.commit();
                LOGGER.info("Removed legacy bills.location_id column from the database.");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to migrate bills table away from location_id: {0}", e.getMessage());
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
     * Wipes all application data, restores the default admin credential, and re-seeds
     * only the minimum system templates needed for runtime flows.
     */
    public static void clearDemoData() throws SQLException {
        try (Connection conn = getConnection()) {
            List<String> tablesToClear = getApplicationTables(conn);
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = OFF;");

                for (String table : tablesToClear) {
                    stmt.executeUpdate("DELETE FROM " + table);
                }

                stmt.executeUpdate("DELETE FROM sqlite_sequence");
                restoreDefaultAdmin(conn);
                seedMessageTemplatesIfNeeded(conn);
                stmt.execute("PRAGMA foreign_keys = ON;");
                conn.commit();
                LOGGER.info("Successfully cleared demo data and restored the default admin account.");
            } catch (SQLException e) {
                conn.rollback();
                throw new SQLException("Failed to clear demo data. Transaction rolled back.", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static void insertMessageTemplate(Connection conn, String key, String subject, String body)
            throws SQLException {
        String sql = """
                INSERT INTO message_templates (template_key, subject, body_template)
                VALUES (?, ?, ?)
                ON CONFLICT(template_key) DO UPDATE SET
                    subject = excluded.subject,
                    body_template = excluded.body_template
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, subject);
            stmt.setString(3, body);
            stmt.executeUpdate();
        }
    }

    private static List<String> getApplicationTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND name NOT LIKE 'sqlite_%'
                ORDER BY name
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private static void restoreDefaultAdmin(Connection conn) throws SQLException {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "admin");
            stmt.setString(2, PasswordHasher.hash("admin"));
            stmt.setString(3, UserRole.ADMIN.name());
            stmt.executeUpdate();
        }
    }
}
