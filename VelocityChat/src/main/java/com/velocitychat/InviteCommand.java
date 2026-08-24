package com.velocitychat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the cross-server server-invite command ({@code /yq}).
 * <p>
 * A player can broadcast an invitation into the cross-server chat, inviting all
 * players on the proxy to join the server the inviter is currently on. Receiving
 * players can click the invitation to connect to that server.
 * <p>
 * Usage: {@code /yq [消息/message]} — message is optional custom invite text.
 * The cooldown and permission bypass are configurable in config.yml:
 * {@code invite-cooldown}, {@code invite-cooldown-bypass}, {@code invite-bypass-permission}.
 */
public class InviteCommand implements SimpleCommand {

    private final ProxyServer server;
    private final ConfigManager config;
    private final ForbiddenWordsManager forbiddenWords;
    private final Logger logger;

    // 冷却追踪 / Cooldown tracking: player UUID -> last invite timestamp (millis)
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public InviteCommand(ProxyServer server, ConfigManager config, ForbiddenWordsManager forbiddenWords, Logger logger) {
        this.server = server;
        this.config = config;
        this.forbiddenWords = forbiddenWords;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // 仅玩家可用 / Players only
        if (!(source instanceof Player player)) {
            ColorUtils.sendMessage(source, logger, config.getMessage("qu_an.chat.message.invite.console"));
            return;
        }

        // 玩家必须已连接到某个后端服务器 / Player must be connected to a server
        if (player.getCurrentServer().isEmpty()) {
            player.sendMessage(Component.text(config.getMessage("qu_an.chat.message.invite.no_server")));
            return;
        }

        String serverId = player.getCurrentServer().get().getServerInfo().getName();
        String serverDisplay = config.getServerDisplayName(serverId);

        // ── 冷却检查 / Cooldown check ──
        int cooldownSeconds = config.getInviteCooldown();
        if (cooldownSeconds > 0) {
            boolean bypass = config.isInviteCooldownBypassEnabled()
                    && player.hasPermission(config.getInviteBypassPermission());
            if (!bypass) {
                long now = System.currentTimeMillis();
                Long lastUsed = cooldowns.get(player.getUniqueId());
                if (lastUsed != null) {
                    long elapsed = (now - lastUsed) / 1000;
                    if (elapsed < cooldownSeconds) {
                        long remaining = cooldownSeconds - elapsed;
                        player.sendMessage(Component.text(
                                config.getMessage("qu_an.chat.message.invite.cooldown", String.valueOf(remaining))));
                        return;
                    }
                }
                cooldowns.put(player.getUniqueId(), now);
            }
        }

        // 邀请文本（邀请者 + 服务器显示名）/ Invite text (inviter + server display name)
        String inviteText = config.getMessage(
                "qu_an.chat.message.invite", player.getUsername(), serverDisplay);

        // 可点击的"点击加入"组件 / Clickable join component
        String clickLabel = config.getMessage("qu_an.chat.message.invite.click");
        // 悬停提示：传入两个占位符，确保 {0}=玩家名、{1}=服务器显示名 都能被替换
        // Hover text: pass both placeholders so {0}=inviter and {1}=server display are resolved
        String hoverText = config.getMessage("qu_an.chat.message.invite.hover",
                player.getUsername(), serverDisplay);
        Component clickComponent = Component.text(clickLabel)
                .clickEvent(ClickEvent.runCommand("/server " + serverId))
                .hoverEvent(HoverEvent.showText(Component.text(hoverText)));

        Component full = Component.text(inviteText + " ").append(clickComponent);

        // 可选的自定义邀请消息 / Optional custom invite message
        if (args.length > 0) {
            String customMessage = String.join(" ", args);

            // ── 违禁词检测 / Forbidden word filter ──
            if (forbiddenWords.isEnabled()
                    && !forbiddenWords.hasBypass(player)
                    && forbiddenWords.containsForbiddenWord(customMessage)) {
                player.sendMessage(Component.text(config.getMessage("qu_an.chat.message.filter.blocked")));
                logger.info("Blocked forbidden invite message from {}: {}", player.getUsername(), customMessage);
                return;
            }

            full = full.append(Component.text(ColorUtils.translate(customMessage)));
        }

        // 广播邀请给所有玩家 / Broadcast the invite to all online players
        for (Player target : server.getAllPlayers()) {
            target.sendMessage(full);
        }

        logger.info("{} invited everyone to join server {}", player.getUsername(), serverId);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // 所有玩家默认可用 / Available to all players by default
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        // 可选的自定义邀请消息 / Optional custom invite message
        if (invocation.arguments().length == 0) {
            return List.of("消息");
        }
        return List.of();
    }
}
