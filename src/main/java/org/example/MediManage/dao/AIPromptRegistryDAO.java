package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.AIPromptVersion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AIPromptRegistryDAO {

    public Optional<AIPromptVersion> findActivePromptVersion(String promptKey) throws SQLException {
        String sql = "SELECT * FROM ai_prompt_registry WHERE prompt_key = ? AND is_active = ? ORDER BY version_number DESC LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, promptKey);
            ps.setBoolean(2, true);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPromptVersion(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<AIPromptVersion> findPromptVersion(String promptKey, int versionNumber) throws SQLException {
        String sql = "SELECT * FROM ai_prompt_registry WHERE prompt_key = ? AND version_number = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, promptKey);
            ps.setInt(2, versionNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPromptVersion(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<AIPromptVersion> listPromptVersions(String promptKey, int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(500, limit <= 0 ? 50 : limit));
        String sql = "SELECT * FROM ai_prompt_registry WHERE prompt_key = ? ORDER BY version_number DESC LIMIT ?";
        List<AIPromptVersion> rows = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, promptKey);
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapPromptVersion(rs));
                }
            }
        }
        return rows;
    }

    public AIPromptVersion seedPromptIfMissing(String promptKey, String defaultTemplate) throws SQLException {
        String selectActive = "SELECT * FROM ai_prompt_registry WHERE prompt_key = ? AND is_active = ? ORDER BY version_number DESC LIMIT 1";
        String selectLatest = "SELECT * FROM ai_prompt_registry WHERE prompt_key = ? ORDER BY version_number DESC LIMIT 1";
        String deactivateSql = "UPDATE ai_prompt_registry SET is_active = ? WHERE prompt_key = ? AND is_active = ?";
        String activateByVersionSql = "UPDATE ai_prompt_registry SET is_active = ? WHERE prompt_key = ? AND version_number = ?";
        String insertSql = "INSERT INTO ai_prompt_registry (" +
                "prompt_key, version_number, template_text, change_type, change_note, rolled_back_from_version, is_active, changed_by_user_id" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement activePs = conn.prepareStatement(selectActive)) {
                    activePs.setString(1, promptKey);
                    activePs.setBoolean(2, true);
                    try (ResultSet activeRs = activePs.executeQuery()) {
                        if (activeRs.next()) {
                            AIPromptVersion existing = mapPromptVersion(activeRs);
                            conn.commit();
                            return existing;
                        }
                    }
                }

                try (PreparedStatement latestPs = conn.prepareStatement(selectLatest)) {
                    latestPs.setString(1, promptKey);
                    try (ResultSet latestRs = latestPs.executeQuery()) {
                        if (latestRs.next()) {
                            AIPromptVersion latest = mapPromptVersion(latestRs);
                            try (PreparedStatement deactivatePs = conn.prepareStatement(deactivateSql);
                                    PreparedStatement activatePs = conn.prepareStatement(activateByVersionSql)) {
                                deactivatePs.setBoolean(1, false);
                                deactivatePs.setString(2, promptKey);
                                deactivatePs.setBoolean(3, true);
                                deactivatePs.executeUpdate();

                                activatePs.setBoolean(1, true);
                                activatePs.setString(2, promptKey);
                                activatePs.setInt(3, latest.versionNumber());
                                activatePs.executeUpdate();
                            }

                            AIPromptVersion activated = findPromptVersionWithConnection(conn, promptKey, latest.versionNumber())
                                    .orElse(latest);
                            conn.commit();
                            return activated;
                        }
                    }
                }

                try (PreparedStatement insertPs = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    insertPs.setString(1, promptKey);
                    insertPs.setInt(2, 1);
                    insertPs.setString(3, defaultTemplate);
                    insertPs.setString(4, "SEED");
                    insertPs.setString(5, "Auto-seeded default prompt template.");
                    insertPs.setObject(6, null);
                    insertPs.setBoolean(7, true);
                    insertPs.setObject(8, null);
                    insertPs.executeUpdate();

                    long id = extractGeneratedId(insertPs);
                    AIPromptVersion created = findPromptVersionByIdWithConnection(conn, id)
                            .orElse(new AIPromptVersion(
                                    id,
                                    promptKey,
                                    1,
                                    defaultTemplate,
                                    "SEED",
                                    "Auto-seeded default prompt template.",
                                    null,
                                    true,
                                    null,
                                    null));
                    conn.commit();
                    return created;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public AIPromptVersion createPromptVersion(
            String promptKey,
            String templateText,
            String changeType,
            String changeNote,
            Integer changedByUserId,
            Integer rolledBackFromVersion) throws SQLException {
        String nextVersionSql = "SELECT COALESCE(MAX(version_number), 0) + 1 FROM ai_prompt_registry WHERE prompt_key = ?";
        String deactivateSql = "UPDATE ai_prompt_registry SET is_active = ? WHERE prompt_key = ? AND is_active = ?";
        String insertSql = "INSERT INTO ai_prompt_registry (" +
                "prompt_key, version_number, template_text, change_type, change_note, rolled_back_from_version, is_active, changed_by_user_id" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int nextVersion = 1;
                try (PreparedStatement nextPs = conn.prepareStatement(nextVersionSql)) {
                    nextPs.setString(1, promptKey);
                    try (ResultSet rs = nextPs.executeQuery()) {
                        if (rs.next()) {
                            nextVersion = rs.getInt(1);
                        }
                    }
                }

                try (PreparedStatement deactivatePs = conn.prepareStatement(deactivateSql)) {
                    deactivatePs.setBoolean(1, false);
                    deactivatePs.setString(2, promptKey);
                    deactivatePs.setBoolean(3, true);
                    deactivatePs.executeUpdate();
                }

                try (PreparedStatement insertPs = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    insertPs.setString(1, promptKey);
                    insertPs.setInt(2, nextVersion);
                    insertPs.setString(3, templateText);
                    insertPs.setString(4, changeType);
                    insertPs.setString(5, changeNote);
                    insertPs.setObject(6, rolledBackFromVersion);
                    insertPs.setBoolean(7, true);
                    insertPs.setObject(8, changedByUserId);
                    insertPs.executeUpdate();

                    long id = extractGeneratedId(insertPs);
                    AIPromptVersion created = findPromptVersionByIdWithConnection(conn, id)
                            .orElse(new AIPromptVersion(
                                    id,
                                    promptKey,
                                    nextVersion,
                                    templateText,
                                    changeType,
                                    changeNote,
                                    rolledBackFromVersion,
                                    true,
                                    changedByUserId,
                                    null));
                    conn.commit();
                    return created;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private Optional<AIPromptVersion> findPromptVersionWithConnection(Connection conn, String promptKey, int versionNumber)
            throws SQLException {
        String sql = "SELECT * FROM ai_prompt_registry WHERE prompt_key = ? AND version_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, promptKey);
            ps.setInt(2, versionNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPromptVersion(rs));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<AIPromptVersion> findPromptVersionByIdWithConnection(Connection conn, long promptVersionId)
            throws SQLException {
        String sql = "SELECT * FROM ai_prompt_registry WHERE prompt_version_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, promptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPromptVersion(rs));
                }
            }
        }
        return Optional.empty();
    }

    private long extractGeneratedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        return 0L;
    }

    private AIPromptVersion mapPromptVersion(ResultSet rs) throws SQLException {
        return new AIPromptVersion(
                rs.getLong("prompt_version_id"),
                rs.getString("prompt_key"),
                rs.getInt("version_number"),
                rs.getString("template_text"),
                rs.getString("change_type"),
                rs.getString("change_note"),
                nullableInt(rs, "rolled_back_from_version"),
                rs.getBoolean("is_active"),
                nullableInt(rs, "changed_by_user_id"),
                rs.getString("created_at"));
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }
}
