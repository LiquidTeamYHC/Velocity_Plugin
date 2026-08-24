package com.velocitychat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the cross-server broadcast command ({@code /br}, {@code /broadcast}).
 * <p>
 * Players can send a message that will be displayed to all players on all backend servers.
 * Supports {@code &amp;} color codes in the message.
 * Available to all players by default (no special permission required).
 */
public class BroadcastCommand implements SimpleCommand {

    private final ProxyServer server;
    private final GroupManager groupManager;
    private final ConfigManager config;
    private final ForbiddenWordsManager forbiddenWords;
    private final Logger logger;

    // 冷却追踪 / Cooldown tracking: player UUID -> last broadcast timestamp (millis)
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public BroadcastCommand(ProxyServer server, GroupManager groupManager, ConfigManager config,
                            ForbiddenWordsManager forbiddenWords, Logger logger) {
        this.server = server;
        this.groupManager = groupManager;
        this.config = config;
        this.forbiddenWords = forbiddenWords;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // Check if arguments are provided
        if (args.length == 0) {
            ColorUtils.sendMessage(source, logger, "§c用法 / Usage: /br <消息/message>");
            return;
        }

        // ── 冷却检查 / Cooldown check ──
        int cooldownSeconds = config.getBroadcastCooldown();
        if (cooldownSeconds > 0 && source instanceof Player player) {
            boolean bypass = config.isCooldownBypassEnabled() && player.hasPermission("velocitychat.admin");
            if (!bypass) {
                long now = System.currentTimeMillis();
                Long lastUsed = cooldowns.get(player.getUniqueId());
                if (lastUsed != null) {
                    long elapsed = (now - lastUsed) / 1000;
                    if (elapsed < cooldownSeconds) {
                        long remaining = cooldownSeconds - elapsed;
                        source.sendMessage(Component.text("§c请等待 " + remaining + " 秒后再使用 /br / §cPlease wait " + remaining + "s before using /br"));
                        return;
                    }
                }
                cooldowns.put(player.getUniqueId(), now);
            }
        }

        // Join all arguments into a single message
        String rawMessage = String.join(" ", args);

        // ── 违禁词检测 / Forbidden word filter ──
        if (source instanceof Player player
                && forbiddenWords.isEnabled()
                && !forbiddenWords.hasBypass(player)
                && forbiddenWords.containsForbiddenWord(rawMessage)) {
            player.sendMessage(Component.text(config.getMessage("qu_an.chat.message.filter.blocked")));
            logger.info("Blocked forbidden message from {}: {}", player.getUsername(), rawMessage);
            return;
        }

        // Get the sender's display name
        String senderName;
        String serverName = "§7Proxy";

        if (source instanceof Player player) {
            senderName = player.getUsername();

            // Check if player has a group title to prepend
            String title = groupManager.getPlayerTitle(player.getUsername());
            if (!title.isEmpty()) {
                senderName = title + " " + senderName;
            }

            // Include the player's current server name if available
            if (player.getCurrentServer().isPresent()) {
                String rawServerId = player.getCurrentServer().get().getServerInfo().getName();
                serverName = config.getServerDisplayName(rawServerId);
            }
        } else {
            senderName = "§c§lConsole";
        }

        // Translate color codes in the message
        String formattedMessage = ColorUtils.translate(rawMessage);

        // Build the broadcast format
        // Format: proxy/server/player: message
        String broadcastFormat = config.getBroadcastFormat();
        String fullMessage = broadcastFormat
                .replace("{0}", senderName)
                .replace("{1}", serverName)
                .replace("{2}", formattedMessage);

        // Send to all players on the proxy (NOT console, to avoid duplicate console output)
        Component component = Component.text(fullMessage);
        for (Player player : server.getAllPlayers()) {
            player.sendMessage(component);
        }

        // Log to console with ANSI colors (replaces the raw server.sendMessage output)
        logger.info(ColorUtils.toAnsi(fullMessage));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // Available to all players and console by default
        CommandSource source = invocation.source();
        if (source instanceof Player player) {
            // Default: everyone can use /br
            // If you want permission control, uncomment:
            // return player.hasPermission("velocitychat.broadcast");
            return true;
        }
        // Console always has permission
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        // 没有参数时显示提示内容；玩家输入后不再显示补全
        if (invocation.arguments().length == 0) {
            return List.of("内容");
        }
        return List.of();
    }
}
