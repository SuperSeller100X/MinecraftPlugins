package org.bukkit.block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
public interface Block {
    Material getType();
    void setType(Material type);
    int getX();
    int getY();
    int getZ();
    World getWorld();
    Location getLocation();
    boolean breakNaturally(ItemStack tool, boolean triggerEffect);
}
