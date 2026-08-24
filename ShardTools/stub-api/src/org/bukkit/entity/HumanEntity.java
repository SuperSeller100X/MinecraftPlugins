package org.bukkit.entity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
public interface HumanEntity extends Entity, CommandSender {
    PlayerInventory getInventory();
    GameMode getGameMode();
    void closeInventory();
    InventoryView openInventory(Inventory inventory);
    Location getLocation();
    World getWorld();
}
