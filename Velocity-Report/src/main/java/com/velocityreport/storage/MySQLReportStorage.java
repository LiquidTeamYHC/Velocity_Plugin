package com.velocityreport.storage;

import com.velocityreport.model.LastSeenEntry;
import com.velocityreport.model.Report;
import com.velocityreport.model.Report.Status;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MySQL-backed storage using HikariCP connection pooling.
 * <p>
 * Automatically handles connection validation, idle eviction,
 * and reconnection — avoids the "wait_timeout / broken pipe" errors
 * that occur with a single persistent JDBC connection.
 */
public class MySQLReportStorage implements ReportStorage {

    private final HikariDataSource dataSource;

    /**
     * @param host     MySQL host (e.g. "127.0.0.1")
     * @param port     MySQL port (usually 3306)
     * @param database Database name
     * @param user     MySQL user
     * @param password MySQL password
     * @param poolConfig Optional HikariCP pool config overrides (may be null)
     */
    public MySQLReportStorage(String host, int port, String database,
                              String user, String password,
                              Map<String, Object> poolConfig) throws SQLException {
        // ── HikariCP pool configuration ──
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC");
        config.setUsername(user);
        config.setPassword(password);

        // Apply defaults, then override with user config if provided
        config.setMaximumPoolSize(intVal(poolConfig, "maximum-pool-size", 10));
        config.setMinimumIdle(intVal(poolConfig, "minimum-idle", 3));
        config.setConnectionTimeout(intVal(poolConfig, "connection-timeout", 5000));
        config.setIdleTimeout(intVal(poolConfig, "idle-timeout", 600_000));
        config.setMaxLifetime(intVal(poolConfig, "max-lifetime", 1_800_000));
        config.setKeepaliveTime(intVal(poolConfig, "keepalive-time", 300_000));
        config.setLeakDetectionThreshold(intVal(poolConfig, "leak-detection-threshold", 30_000));

        config.setPoolName("VelocityReport-MySQL");
        config.setConnectionTestQuery("SELECT 1");                   // explicit validation for MariaDB fallback

        // Driver must be registered before HikariCP auto-discovers it
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found", e);
        }

        dataSource = new HikariDataSource(config);

        // Create table if not exists
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reports (
                    id                    INT AUTO_INCREMENT PRIMARY KEY,
                    reporter_uuid         VARCHAR(36) NOT NULL,
                    reporter_name         VARCHAR(64) NOT NULL,
                    reported_uuid         VARCHAR(36) NOT NULL,
                    reported_name         VARCHAR(64) NOT NULL,
                    reason                TEXT NOT NULL,
                    server_name           VARCHAR(128) NOT NULL,
                    reported_server_name  VARCHAR(128),
                    timestamp             BIGINT NOT NULL,
                    status                VARCHAR(16) NOT NULL DEFAULT 'OPEN',
                    handler_uuid          VARCHAR(36),
                    handler_name          VARCHAR(64),
                    resolution            TEXT,
                    handled_at            BIGINT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Last-seen records — one row per player, used for offline reports
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_last_seen (
                    player_uuid  VARCHAR(36) PRIMARY KEY,
                    player_name  VARCHAR(64) NOT NULL,
                    seen_at      BIGINT NOT NULL,
                    last_server  VARCHAR(128),
                    INDEX idx_player_last_seen_name (player_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
        }
    }

    @Override
    public int createReport(Report report) throws SQLException {
        String sql = """
            INSERT INTO reports
                (reporter_uuid, reporter_name, reported_uuid, reported_name,
                 reason, server_name, reported_server_name, timestamp, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, report.getReporterUuid().toString());
            ps.setString(2, report.getReporterName());
            ps.setString(3, report.getReportedUuid().toString());
            ps.setString(4, report.getReportedName());
            ps.setString(5, report.getReason());
            ps.setString(6, report.getServerName());
            ps.setString(7, report.getReportedServerName());
            ps.setLong(8, report.getTimestamp());
            ps.setString(9, report.getStatus().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    report.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated report ID");
    }

    @Override
    public Report getReport(int id) throws SQLException {
        String sql = "SELECT * FROM reports WHERE id = ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return fromResultSet(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Report> getOpenReports(int page, int perPage) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE status = 'OPEN' ORDER BY id DESC LIMIT ? OFFSET ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, perPage);
            ps.setInt(2, (page - 1) * perPage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reports.add(fromResultSet(rs));
                }
            }
        }
        return reports;
    }

    @Override
    public int getOpenReportCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports WHERE status = 'OPEN';";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    @Override
    public boolean closeReport(int id, Status status,
                               UUID handlerUuid, String handlerName,
                               String resolution) throws SQLException {
        String sql = """
            UPDATE reports SET status = ?, handler_uuid = ?, handler_name = ?,
                resolution = ?, handled_at = ?
            WHERE id = ?;
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, handlerUuid.toString());
            ps.setString(3, handlerName);
            ps.setString(4, resolution);
            ps.setLong(5, System.currentTimeMillis());
            ps.setInt(6, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public int getReportCountSince(UUID reporterUuid, long since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports WHERE reporter_uuid = ? AND timestamp > ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reporterUuid.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public boolean reopenReport(int id) throws SQLException {
        String sql = "UPDATE reports SET status = 'OPEN', handler_uuid = NULL, handler_name = NULL, resolution = NULL, handled_at = 0 WHERE id = ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<Report> getAllReports(int page, int perPage) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports ORDER BY id DESC LIMIT ? OFFSET ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, perPage);
            ps.setInt(2, (page - 1) * perPage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reports.add(fromResultSet(rs));
                }
            }
        }
        return reports;
    }

    @Override
    public int getTotalReportCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports;";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    @Override
    public List<Report> getReportsByPlayerName(String playerName, int page, int perPage) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE LOWER(reporter_name) = LOWER(?) OR LOWER(reported_name) = LOWER(?) ORDER BY id DESC LIMIT ? OFFSET ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            ps.setInt(3, perPage);
            ps.setInt(4, (page - 1) * perPage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reports.add(fromResultSet(rs));
                }
            }
        }
        return reports;
    }

    @Override
    public int getReportCountByPlayerName(String playerName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports WHERE LOWER(reporter_name) = LOWER(?) OR LOWER(reported_name) = LOWER(?);";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    // ── Last-seen tracking ───────────────────────────────────

    @Override
    public void updateLastSeen(UUID playerUuid, String playerName,
                               long timestamp, String serverName) throws SQLException {
        String sql = """
            INSERT INTO player_last_seen (player_uuid, player_name, seen_at, last_server)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                seen_at     = VALUES(seen_at),
                last_server = COALESCE(VALUES(last_server), last_server);
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setLong(3, timestamp);
            ps.setString(4, serverName);
            ps.executeUpdate();
        }
    }

    @Override
    public LastSeenEntry getLastSeenByName(String playerName) throws SQLException {
        String sql = """
            SELECT player_uuid, player_name, seen_at, last_server
            FROM player_last_seen WHERE LOWER(player_name) = LOWER(?) LIMIT 1;
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LastSeenEntry(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getLong("seen_at"),
                        rs.getString("last_server"));
                }
            }
        }
        return null;
    }

    @Override
    public List<String> getNamesSeenSince(long since, String prefix, int limit) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = """
            SELECT player_name FROM player_last_seen
            WHERE seen_at > ? AND player_name LIKE ? ESCAPE '\\'
            ORDER BY seen_at DESC LIMIT ?;
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, since);
            ps.setString(2, escapeLike(prefix) + "%");
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("player_name"));
                }
            }
        }
        return names;
    }

    /** Escapes LIKE wildcards so that a name prefix matches literally. */
    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Extracts an integer value from the pool config map, falling back to the default if missing.
     */
    private static int intVal(Map<String, Object> poolConfig, String key, int defaultValue) {
        if (poolConfig == null) return defaultValue;
        Object val = poolConfig.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private static Report fromResultSet(ResultSet rs) throws SQLException {
        return new Report(
            rs.getInt("id"),
            UUID.fromString(rs.getString("reporter_uuid")),
            rs.getString("reporter_name"),
            UUID.fromString(rs.getString("reported_uuid")),
            rs.getString("reported_name"),
            rs.getString("reason"),
            rs.getString("server_name"),
            rs.getString("reported_server_name"),
            rs.getLong("timestamp"),
            Status.valueOf(rs.getString("status")),
            rs.getString("handler_uuid") != null ? UUID.fromString(rs.getString("handler_uuid")) : null,
            rs.getString("handler_name"),
            rs.getString("resolution"),
            rs.getLong("handled_at")
        );
    }
}
