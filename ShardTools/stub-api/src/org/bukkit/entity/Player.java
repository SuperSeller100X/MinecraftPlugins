package org.bukkit.entity;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.inventory.InventoryView;
import org.bukkit.potion.PotionEffect;
public interface Player extends HumanEntity, org.bukkit.OfflinePlayer {
    UUID getUniqueId();
    void sendMessage(Component message);
    void sendActionBar(Component message);
    void playSound(Location location, Sound sound, float volume, float pitch);
    void updateInventory();
    InventoryView getOpenInventory();
    boolean isOnline();
    boolean isSneaking();
    boolean addPotionEffect(PotionEffect effect);
}
