package com.velocityreport.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocityreport.ReportManager;

/**
 * Listens for player events.
 * <p>
 * Notifies staff of pending reports when they join, and keeps the last-seen
 * records up to date so that offline players can still be reported.
 * <p>
 * The handlers run asynchronously because they touch the database.
 */
public class PlayerListener {

    private final ReportManager manager;

    public PlayerListener(ReportManager manager) {
        this.manager = manager;
    }

    @Subscribe(async = true)
    public void onPostLogin(PostLoginEvent event) {
        // PostLoginEvent fires after the player has fully joined the proxy,
        // so we can safely send them a message directly.
        manager.notifyStaffOnJoin(event.getPlayer());

        // Not on a backend server yet — keep the server stored last time.
        // Still worth recording: it is the only sighting of this player if the
        // proxy is killed before the disconnect event can fire.
        manager.recordLastSeen(event.getPlayer(), null);
    }

    @Subscribe(async = true)
    public void onServerConnected(ServerConnectedEvent event) {
        // Remembers which server the player was on last, for later offline reports.
        manager.recordLastSeen(event.getPlayer(), event.getServer().getServerInfo().getName());
    }

    @Subscribe(async = true)
    public void onDisconnect(DisconnectEvent event) {
        // The player may already be off the backend server here, so only the
        // timestamp is refreshed — the server name from onServerConnected stays.
        manager.recordLastSeen(event.getPlayer(), null);
    }
}
