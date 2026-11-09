package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class PlayerRiptideEvent extends PlayerEvent implements Cancellable {
    private final Player player;
    private final ItemStack item;
    private final Vector velocity;
    private boolean cancelled;
    public PlayerRiptideEvent(Player player, ItemStack item, Vector velocity) {
        this.player = player; this.item = item; this.velocity = velocity;
    }
    @Override public Player getPlayer() { return player; }
    public ItemStack getItem() { return item; }
    public Vector getVelocity() { return velocity.clone(); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean value) { cancelled = value; }
}
