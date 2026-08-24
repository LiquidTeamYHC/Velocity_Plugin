package com.velocitychat;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * VelocityChat — A cross-server proxy chat plugin for Velocity.
 * <p>
 * Features:
 * <ul>
 *   <li>{@code /br <message>} — Cross-server broadcast with {@code &amp;} color code support</li>
 *   <li>Player join / server switch / disconnect announcements with custom server aliases</li>
 * </ul>
 * <p>
 * Only needs to be installed on the Velocity proxy; no backend server plugins required.
 */
@Plugin(
        id = "velocity-chat",
        name = "VelocityChat",
        version = "2.1.0",
        description = "Cross-server proxy chat plugin — broadcast, join/switch/leave messages, server invites, forbidden-word filter, timed announcements with server aliases",
        authors = {"YuHongChen(LiquidTeam) QQ:1464670605"},
        url = "https://github.com/LiquidTeamYHC/Velocity_Plugin/tree/main/VelocityChat",
        dependencies = {
                @Dependency(id = "luckperms", optional = true)
        }
)
public class VelocityChat {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private GroupManager groupManager;
    private ForbiddenWordsManager forbiddenWordsManager;
    private AutoBroadcast autoBroadcast;

    @Inject
    public VelocityChat(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
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

        // ── Initialize config & language manager ──
        configManager = new ConfigManager(logger, dataDirectory);
        configManager.loadConfig();


        // ── Initialize group manager ──
        groupManager = new GroupManager(logger, dataDirectory);
        groupManager.load();

        // ── Initialize forbidden-word filter ──
        forbiddenWordsManager = new ForbiddenWordsManager(logger, dataDirectory);
        forbiddenWordsManager.load(configManager);

        // ── Initialize timed announcements ──
        autoBroadcast = new AutoBroadcast(this, server, logger, dataDirectory);
        autoBroadcast.load();
        autoBroadcast.start();

        // ── Register commands ──
        registerCommands();

        // ── Register event listeners ──
        server.getEventManager().register(this, new PlayerListener(server, configManager, logger));

        logger.info("VelocityChat v2.1.0 enabled");
        logger.info("Author: YuHongChen(LiquidTeam) QQ:1464670605");
    }

    /**
     * Returns the group manager instance.
     */
    public GroupManager getGroupManager() {
        return groupManager;
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        autoBroadcast.stop();
        logger.info("VelocityChat disabled");
    }

    // ── Command Registration ─────────────────────────────────

    private void registerCommands() {
        var cmdManager = server.getCommandManager();

        // Get broadcast aliases from config; always ensure /k is registered
        var aliases = new ArrayList<>(configManager.getBroadcastAliases());
        if (!aliases.contains("k")) {
            aliases.add("k");
        }

        // Use the first alias as the primary command name, rest as aliases
        String primary = aliases.isEmpty() ? "br" : aliases.get(0);
        var extraAliases = aliases.size() > 1
                ? aliases.subList(1, aliases.size()).toArray(new String[0])
                : new String[0];

        var meta = cmdManager.metaBuilder(primary)
                .aliases(extraAliases)
                .plugin(this)
                .build();

        cmdManager.register(meta, new BroadcastCommand(server, groupManager, configManager, forbiddenWordsManager, logger));
        logger.info("Registered broadcast command: /{} (aliases: {})", primary,
                String.join(", ", aliases));

        // Register /velocitychat admin command (with /vchat alias)
        var vchatMeta = cmdManager.metaBuilder("velocitychat")
                .aliases("vchat")
                .plugin(this)
                .build();
        cmdManager.register(vchatMeta, new VelocityChatCommand(server, groupManager, configManager, forbiddenWordsManager, autoBroadcast, logger));
        logger.info("Registered admin command: /velocitychat (/vchat)");

        // Register /yq cross-server invite command
        var inviteMeta = cmdManager.metaBuilder("yq")
                .plugin(this)
                .build();
        cmdManager.register(inviteMeta, new InviteCommand(server, configManager, forbiddenWordsManager, logger));
        logger.info("Registered invite command: /yq");
    }

    /**
     * Returns the plugin's configuration manager instance.
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Returns the forbidden-word filter manager instance.
     */
    public ForbiddenWordsManager getForbiddenWordsManager() {
        return forbiddenWordsManager;
    }

    /**
     * Returns the timed announcement manager instance.
     */
    public AutoBroadcast getAutoBroadcast() {
        return autoBroadcast;
    }
}
