package com.velocityreport.storage;

import com.velocityreport.model.LastSeenEntry;
import com.velocityreport.model.Report;
import com.velocityreport.model.Report.Status;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite-backed storage.
 * Uses WAL mode for better read concurrency.
 * All write operations are synchronized to avoid SQLite concurrent-modification issues.
 */
public class SQLiteReportStorage implements ReportStorage {

    private final Connection connection;

    public SQLiteReportStorage(Path dbPath) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS reports (
                id                    INTEGER PRIMARY KEY AUTOINCREMENT,
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
            );
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }

        // Last-seen records — one row per player, used for offline reports
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_last_seen (
                    player_uuid  VARCHAR(36) PRIMARY KEY,
                    player_name  VARCHAR(64) NOT NULL,
                    seen_at      BIGINT NOT NULL,
                    last_server  VARCHAR(128)
                );
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_last_seen_name ON player_last_seen (player_name);");
        }
    }

    @Override
    public synchronized int createReport(Report report) throws SQLException {
        String sql = """
            INSERT INTO reports
                (reporter_uuid, reporter_name, reported_uuid, reported_name,
                 reason, server_name, reported_server_name, timestamp, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    @Override
    public synchronized boolean closeReport(int id, Status status,
                                            UUID handlerUuid, String handlerName,
                                            String resolution) throws SQLException {
        String sql = """
            UPDATE reports SET status = ?, handler_uuid = ?, handler_name = ?,
                resolution = ?, handled_at = ?
            WHERE id = ?;
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<Report> getAllReports(int page, int perPage) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports ORDER BY id DESC LIMIT ? OFFSET ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    @Override
    public List<Report> getReportsByPlayerName(String playerName, int page, int perPage) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE LOWER(reporter_name) = LOWER(?) OR LOWER(reported_name) = LOWER(?) ORDER BY id DESC LIMIT ? OFFSET ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
    public synchronized void updateLastSeen(UUID playerUuid, String playerName,
                                            long timestamp, String serverName) throws SQLException {
        String sql = """
            INSERT INTO player_last_seen (player_uuid, player_name, seen_at, last_server)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                player_name = excluded.player_name,
                seen_at     = excluded.seen_at,
                last_server = COALESCE(excluded.last_server, player_last_seen.last_server);
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
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
