package com.velocityreport.storage;

import com.velocityreport.model.LastSeenEntry;
import com.velocityreport.model.Report;
import com.velocityreport.model.Report.Status;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Storage interface for report persistence.
 * Implementations: {@link SQLiteReportStorage}, {@link MySQLReportStorage}.
 */
public interface ReportStorage {

    /** Inserts a new report and sets its generated ID. Returns the new report ID. */
    int createReport(Report report) throws SQLException;

    /** Fetches a single report by ID, or null if not found. */
    Report getReport(int id) throws SQLException;

    /** Returns a paginated list of open reports, newest first. */
    List<Report> getOpenReports(int page, int perPage) throws SQLException;

    /** Returns the total number of open reports. */
    int getOpenReportCount() throws SQLException;

    /** Closes a report with the given status, handler info, and resolution. */
    boolean closeReport(int id, Status status, UUID handlerUuid, String handlerName, String resolution) throws SQLException;

    /** Re-opens a report (sets status back to OPEN). */
    boolean reopenReport(int id) throws SQLException;

    /** Counts reports by a reporter since a given timestamp (for cooldown). */
    int getReportCountSince(UUID reporterUuid, long since) throws SQLException;

    // ── History / management ─────────────────────────────────

    /** Returns a paginated list of ALL reports (all statuses), newest first. */
    List<Report> getAllReports(int page, int perPage) throws SQLException;

    /** Total number of reports (all statuses). */
    int getTotalReportCount() throws SQLException;

    /** Returns reports where the given player is reporter OR reported, paginated. */
    List<Report> getReportsByPlayerName(String playerName, int page, int perPage) throws SQLException;

    /** Count of reports where the given player is reporter OR reported. */
    int getReportCountByPlayerName(String playerName) throws SQLException;

    // ── Last-seen tracking (offline report support) ───────────

    /**
     * Inserts or updates a player's last-seen record.
     *
     * @param serverName the server the player was on, or null to keep the stored one
     */
    void updateLastSeen(UUID playerUuid, String playerName, long timestamp, String serverName) throws SQLException;

    /** Looks a player up by name (case-insensitive), or null if they were never seen. */
    LastSeenEntry getLastSeenByName(String playerName) throws SQLException;

    /** Names seen since the given timestamp whose name starts with the given prefix, newest first. */
    List<String> getNamesSeenSince(long since, String prefix, int limit) throws SQLException;

    /** Closes the database connection. */
    void close();
}
