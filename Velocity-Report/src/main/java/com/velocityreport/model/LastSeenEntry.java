package com.velocityreport.model;

import java.util.UUID;

/**
 * A player's last-seen record.
 * <p>
 * Velocity keeps no offline player data, so the plugin writes one of these down
 * every time a player is seen on the proxy. It is what lets offline players be
 * reported while their record is still inside the configured time window.
 */
public class LastSeenEntry {

    private final UUID uuid;
    private final String name;
    private final long lastSeen;
    private final String server;

    public LastSeenEntry(UUID uuid, String name, long lastSeen, String server) {
        this.uuid = uuid;
        this.name = name;
        this.lastSeen = lastSeen;
        this.server = server;
    }

    public UUID getUuid() { return uuid; }

    /** The name the player had the last time they were seen. */
    public String getName() { return name; }

    /** Epoch milliseconds of the last time this player was seen on the proxy. */
    public long getLastSeen() { return lastSeen; }

    /** The server the player was on when last seen, or null if it was never recorded. */
    public String getServer() { return server; }
}
