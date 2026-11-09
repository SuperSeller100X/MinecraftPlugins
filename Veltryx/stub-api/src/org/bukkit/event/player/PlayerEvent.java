package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public abstract class PlayerEvent extends Event {
    public Player getPlayer() { return null; }
}
