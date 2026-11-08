package org.bukkit.entity;

import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public interface Player extends LivingEntity, OfflinePlayer, org.bukkit.command.CommandSender {
    int getPing();
    GameMode getGameMode();
    boolean isFlying();
    org.bukkit.inventory.PlayerInventory getInventory();
    boolean isSwimming();
    Location getEyeLocation();
    void kick(Component reason);
    void sendActionBar(Component message);
    void setScoreboard(org.bukkit.scoreboard.Scoreboard scoreboard);
    org.bukkit.scoreboard.Scoreboard getScoreboard();
    void closeInventory();
    Object openInventory(Inventory inventory);
    CompletableFuture<Boolean> teleportAsync(Location location);
    void sendBlockChange(Location location, org.bukkit.block.data.BlockData block);
    void hidePlayer(Plugin plugin, Player player);
    void showPlayer(Plugin plugin, Player player);
}
