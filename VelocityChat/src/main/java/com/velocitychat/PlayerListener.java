package com.velocitychat;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for player connection, disconnection, and server switch events,
 * then broadcasts join/switch/leave messages to all online players.
 */
public class PlayerListener {

    private final ProxyServer server;
    private final ConfigManager config;
    private final Logger logger;

    // 缓存玩家最后连接的服务器（解决 DisconnectEvent 时 getCurrentServer 可能为空的问题）
    private final Map<UUID, String> lastServer = new ConcurrentHashMap<>();

    public PlayerListener(ProxyServer server, ConfigManager config, Logger logger) {
        this.server = server;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Called when a player connects to a backend server (including initial join).
     * Broadcasts a join message to all players on the proxy.
     */
    @Subscribe(order = PostOrder.EARLY)
    public void onPlayerConnect(ServerConnectedEvent event) {
        // 玩家连接时主动检查权限，让 LuckPerms 记录到补全列表
        Player player = event.getPlayer();
        player.hasPermission("velocitychat.admin");
        player.hasPermission("velocitychat.admin.create");
        player.hasPermission("velocitychat.admin.group.join");
        player.hasPermission("velocitychat.admin.group.remove");
        player.hasPermission("velocitychat.admin.group.settitle");
        player.hasPermission("velocitychat.admin.group.delete");
        player.hasPermission("velocitychat.admin.group.list");
        player.hasPermission("velocitychat.admin.reload");
        player.hasPermission("velocitychat.broadcast");
        player.hasPermission("velocitychat.invite");
        player.hasPermission("velocitychat.invite.bypass");
    }

    @Subscribe(order = PostOrder.LATE)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer currentServer = event.getServer();
        String currentName = currentServer.getServerInfo().getName();
        String displayName = config.getServerDisplayName(currentName);

        // 记录玩家当前服务器，供断线时使用
        lastServer.put(player.getUniqueId(), currentName);

        // Get the previous server, if any (to distinguish join vs switch)
        Optional<RegisteredServer> previousServer = event.getPreviousServer();

        if (previousServer.isPresent()) {
            // Player switched servers
            String oldName = previousServer.get().getServerInfo().getName();
            String oldDisplayName = config.getServerDisplayName(oldName);

            String switchMsg = config.getMessage(
                    "qu_an.chat.message.server_switch",
                    player.getUsername(),
                    oldDisplayName,
                    displayName
            );

            broadcastMessage(switchMsg);
        } else {
            // Player joined the proxy for the first time
            String joinMsg = config.getMessage(
                    "qu_an.chat.message.connected",
                    player.getUsername(),
                    displayName
            );

            broadcastMessage(joinMsg);
        }
    }

    /**
     * Called when a player disconnects from the proxy.
     * Broadcasts a leave message to all remaining players.
     * <p>
     * 如果玩家从未连接到任何后端服务器（例如被封禁后连接被拒），
     * 不广播离开消息，避免显示 "xxx离开了unknown"。
     */
    @Subscribe(order = PostOrder.LATE)
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // 从缓存取最后所在的服务器（DisconnectEvent 时 getCurrentServer 可能已为空）
        String serverName = lastServer.remove(player.getUniqueId());
        if (serverName == null) {
            serverName = player.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse(null);
        }

        // 如果玩家从未连接到任何后端服务器（如封禁玩家连接被拒），不广播离开消息
        if (serverName == null) {
            return;
        }

        String displayName = config.getServerDisplayName(serverName);

        String leaveMsg = config.getMessage(
                "qu_an.chat.message.disconnect",
                player.getUsername(),
                displayName
        );

        // Broadcast to all remaining online players (the disconnecting player won't see it)
        broadcastMessage(leaveMsg);
    }

    /**
     * Handles the case where a player is kicked from a server.
     * If they have another server to fall back to, it's handled by ServerConnectedEvent.
     * If they're disconnected entirely, DisconnectEvent handles it.
     * We just log the kick here.
     */
    @Subscribe(order = PostOrder.LATE)
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        RegisteredServer kickedFrom = event.getServer();

        logger.info("{} was kicked from server {}",
                player.getUsername(), kickedFrom.getServerInfo().getName());
    }

    /**
     * Sends a join/switch/leave message according to the configured notify-mode.
     * <p>
     * notify-mode: all = send to everyone (default)
     *              admin = send only to players with velocitychat.admin permission
     *              none = don't send to anyone
     *
     * @param message The formatted message string (with § color codes)
     */
    private void broadcastMessage(String message) {
        String mode = config.getNotifyMode();
        if (mode.equals("none")) return;

        Component component = Component.text(message);

        // 发送给玩家（不发送到控制台）/ Send to players only (not console)
        for (Player player : server.getAllPlayers()) {
            if (mode.equals("admin") && !player.hasPermission("velocitychat.admin")) {
                continue; // admin 模式：跳过非管理员
            }
            player.sendMessage(component);
        }
    }
}
