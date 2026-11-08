package org.bukkit.entity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
public interface HumanEntity extends LivingEntity, CommandSender {
    default void setItemOnCursor(org.bukkit.inventory.ItemStack item) {}
    PlayerInventory getInventory();
    GameMode getGameMode();
    void closeInventory();
    InventoryView openInventory(Inventory inventory);
    InventoryView getOpenInventory();
    Location getLocation();
    World getWorld();
}
