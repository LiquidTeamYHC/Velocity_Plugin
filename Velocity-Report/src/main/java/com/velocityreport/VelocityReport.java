package com.velocityreport;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocityreport.command.*;
import com.velocityreport.listener.PlayerListener;
import com.velocityreport.storage.MySQLReportStorage;
import com.velocityreport.storage.ReportStorage;
import com.velocityreport.storage.SQLiteReportStorage;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Plugin(
    id = "velocity-report",
    name = "VelocityReport",
    version = "2.3.0",
    description = "Cross-server report plugin for Velocity proxy",
    authors = {"YuHongChen(LiquidTeam)"},
    url = "https://github.com/LiquidTeamYHC/Velocity_Plugin/tree/main/Velocity-Report"
)
public class VelocityReport {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ReportStorage storage;
    private ReportManager manager;
    private LanguageManager languageManager;
    private StaffManager staffManager;
    private Map<String, Object> config;  // raw config

    @Inject
    public VelocityReport(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // ── Ensure data directory exists ──
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.error("Failed to create plugin data directory", e);
            return;
        }

        // ── Initialize language manager ──
        languageManager = new LanguageManager(logger, dataDirectory);

        // ── Load raw config (needed before storage init for DB settings) ──
        if (!loadConfigFile()) {
            // If config loading entirely failed, we still try with defaults
            logger.warn("Failed to load config, using built-in defaults");
        }

        // ── Initialize storage based on config ──
        try {
            storage = createStorageFromConfig();
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            return;
        }

        // ── Initialize manager (with LanguageManager + staff list) ──
        staffManager = new StaffManager(logger, dataDirectory);
        staffManager.load();
        manager = new ReportManager(server, storage, logger, languageManager, staffManager);

        // ── Apply config settings to manager ──
        applyConfig();

        // ── Register commands ──
        var cmdManager = server.getCommandManager();
        var metaReport = cmdManager.metaBuilder("report")
                .aliases("rep")
                .plugin(this)
                .build();
        cmdManager.register(metaReport, new ReportCommand(manager));

        var metaReports = cmdManager.metaBuilder("reports")
                .aliases("reportslist", "reportlist")
                .plugin(this)
                .build();
        cmdManager.register(metaReports, new ReportsCommand(manager));

        var metaView = cmdManager.metaBuilder("reportview")
                .plugin(this)
                .build();
        cmdManager.register(metaView, new ReportViewCommand(manager));

        var metaClose = cmdManager.metaBuilder("reportclose")
                .aliases("closereport", "resolvereport")
                .plugin(this)
                .build();
        cmdManager.register(metaClose, new ReportCloseCommand(manager));

        var staffCommand = new StaffCommand(manager, server);
        var metaStaff = cmdManager.metaBuilder("reportstaff")
                .plugin(this)
                .build();
        cmdManager.register(metaStaff, staffCommand);

        // Single entry point for all staff actions; the commands above stay as they are.
        // Config reloading lives here too (/reportadmin reload) — the old /reportreload
        // command was removed in 2.3.0.
        var metaAdmin = cmdManager.metaBuilder("reportadmin")
                .aliases("reportadm", "ra")
                .plugin(this)
                .build();
        cmdManager.register(metaAdmin, new ReportAdminCommand(this, manager, staffCommand));

        var metaHistory = cmdManager.metaBuilder("reportcd")
                .plugin(this)
                .build();
        cmdManager.register(metaHistory, new ReportHistoryCommand(manager, server));

        // ── Register listener ──
        server.getEventManager().register(this, new PlayerListener(manager));

        logger.info("VelocityReport v2.3.0 enabled");
        logger.info("Author: YuHongChen(LiquidTeam) | 作者QQ:1464670605");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (storage != null) {
            storage.close();
            logger.info("Database connection closed");
        }
        logger.info("VelocityReport disabled");
    }

    // ── Public API for commands ──────────────────────────────

    /**
     * Returns the plugin's language manager instance.
     */
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    /**
     * Convenience: get a localized message by key.
     * Delegates to {@link LanguageManager#getMessage(String)}.
     */
    public String getMessage(String key) {
        return languageManager != null ? languageManager.getMessage(key) : "<red>Missing message: " + key + "</red>";
    }

    // ── Storage factory ──────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ReportStorage createStorageFromConfig() throws Exception {
        if (config == null) {
            // No config → default to SQLite
            Path dbPath = dataDirectory.resolve("reports.db");
            logger.info("Using SQLite (default): {}", dbPath.toAbsolutePath());
            return new SQLiteReportStorage(dbPath);
        }

        Map<String, Object> db = (Map<String, Object>) config.get("database");
        if (db == null) {
            Path dbPath = dataDirectory.resolve("reports.db");
            logger.info("Using SQLite (no database section in config)");
            return new SQLiteReportStorage(dbPath);
        }

        String type = db.getOrDefault("type", "sqlite").toString().toLowerCase();

        if ("mysql".equals(type)) {
            Map<String, Object> mysql = (Map<String, Object>) db.get("mysql");
            if (mysql == null) {
                throw new RuntimeException("database.type is 'mysql' but database.mysql section is missing!");
            }

            String host = mysql.getOrDefault("host", "127.0.0.1").toString();
            int port = parseInt(mysql.get("port"), 3306);
            String database = mysql.getOrDefault("database", "velocity_report").toString();
            String user = mysql.getOrDefault("user", "root").toString();
            String password = mysql.getOrDefault("password", "").toString();

            @SuppressWarnings("unchecked")
            Map<String, Object> poolConfig = (Map<String, Object>) mysql.get("pool");

            logger.info("Using MySQL: {}:{}/{}", host, port, database);
            return new MySQLReportStorage(host, port, database, user, password, poolConfig);
        }

        // Default: SQLite
        Map<String, Object> sqlite = (Map<String, Object>) db.get("sqlite");
        String filename = sqlite != null
                ? sqlite.getOrDefault("filename", "reports.db").toString()
                : "reports.db";
        Path dbPath = dataDirectory.resolve(filename);
        logger.info("Using SQLite: {}", dbPath.toAbsolutePath());
        return new SQLiteReportStorage(dbPath);
    }

    // ── Config loading ───────────────────────────────────────

    /** Returns true if config was loaded successfully (or default was written). */
    @SuppressWarnings("unchecked")
    private boolean loadConfigFile() {
        Path configFile = dataDirectory.resolve("config.yml");

        // Write default config if not present
        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile);
                } else {
                    String fallback = "language: zh\ncooldown-seconds: 60\nreports-per-page: 10\n";
                    Files.writeString(configFile, fallback);
                }
            } catch (IOException e) {
                logger.warn("Could not write default config.yml", e);
                return false;
            }
        }

        // Load
        try (InputStream in = Files.newInputStream(configFile)) {
            Yaml yaml = new Yaml();
            config = yaml.load(in);
            logger.info("Configuration loaded from {}", configFile.getFileName());
            return config != null;
        } catch (Exception e) {
            logger.warn("Failed to parse config.yml", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void applyConfig() {
        if (config == null) return;

        // ── Load language ──
        String lang = config.getOrDefault("language", "zh").toString();
        if (languageManager != null) {
            languageManager.loadLanguage(lang);
        }

        // Simple values (apply before creating manager if needed)
        if (manager == null) return;

        if (config.get("cooldown-seconds") instanceof Number n) {
            manager.setCooldownSeconds(n.intValue());
        }
        if (config.get("reports-per-page") instanceof Number n) {
            manager.setReportsPerPage(n.intValue());
        }
        if (config.get("offline-report-window-hours") instanceof Number n) {
            manager.setOfflineReportWindowHours(n.intValue());
        }

        // Staff users list (for servers without a permission plugin)
        // Empty by default; an absent or emptied list clears the names on reload.
        manager.setStaffUsers(config.get("staff-users") instanceof List<?> list
                ? (List<String>) (List<?>) list
                : List.of());

        // Server aliases (raw server name → display text)
        applyServerAliases();

        // Report reasons (name and description come from language files)
        if (config.get("report-reasons") instanceof List<?> list) {
            List<ReportManager.ReportReason> reasons = new java.util.ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    String id = getStr(m, "id");
                    String icon = getStr(m, "icon");
                    if (id != null) {
                        reasons.add(new ReportManager.ReportReason(
                            id, icon != null ? icon : "📋"));
                    }
                }
            }
            manager.setReportReasons(reasons);
        }

        // Messages section — override language file messages
        if (config.get("messages") instanceof Map<?, ?> msgs) {
            for (var entry : msgs.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                    if (languageManager != null) {
                        languageManager.setMessage(key, value);
                    }
                }
            }
        }
    }

    /**
     * Reads the {@code server-aliases} section.
     * <p>
     * Current format is a map of server name → display text. The format used
     * before 2.3.0 was a plain list of server names whose display text lived in
     * the language file ({@code server-alias-<name>}); that is still accepted so
     * existing configs keep working.
     */
    @SuppressWarnings("unchecked")
    private void applyServerAliases() {
        Object node = config.get("server-aliases");

        if (node instanceof Map<?, ?> map) {
            Map<String, String> aliases = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    aliases.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            manager.setServerAliases(aliases);
        } else if (node instanceof List<?> names) {
            manager.setLegacyServerAliases((List<String>) (List<?>) names);
        } else {
            manager.setServerAliases(Map.of());
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Re-reads config.yml and applies settings without restarting. */
    public void reloadConfig() {
        if (!loadConfigFile()) {
            logger.warn("Config reload failed, keeping previous settings");
            return;
        }
        applyConfig();
        logger.info("Configuration reloaded successfully");
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static String getStr(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
