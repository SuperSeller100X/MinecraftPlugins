package io.papermc.paper.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerStopUsingItemEvent extends PlayerEvent {
    private final Player player;
    private final ItemStack item;
    private final int ticksHeldFor;
    public PlayerStopUsingItemEvent(Player player, ItemStack item, int ticksHeldFor) {
        this.player = player; this.item = item; this.ticksHeldFor = ticksHeldFor;
    }
    @Override public Player getPlayer() { return player; }
    public ItemStack getItem() { return item; }
    public int getTicksHeldFor() { return ticksHeldFor; }
}
