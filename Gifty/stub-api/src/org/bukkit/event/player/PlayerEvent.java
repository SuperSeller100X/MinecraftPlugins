package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public abstract class PlayerEvent extends Event {
    private final Player player;

    public PlayerEvent(Player who) {
        this.player = who;
    }

    public Player getPlayer() {
        return player;
    }
}
