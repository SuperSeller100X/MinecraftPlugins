package org.bukkit.event.player;

import java.util.UUID;
import org.bukkit.event.Event;

public class AsyncPlayerPreLoginEvent extends Event {
    public enum Result { ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER }
    public UUID getUniqueId() { return null; }
    public String getName() { return null; }
    public void disallow(Result result, String message) {}
}
