package dev.superseller.chestlock.service;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.model.LockData;
import dev.superseller.chestlock.storage.LockStore;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Creates and validates transferable, single-lock physical keys. */
public final class KeyService {
    private final ChestLockPlugin plugin;
    private final LockStore lockStore;

    public KeyService(ChestLockPlugin plugin, LockStore lockStore) {
        this.plugin = plugin;
        this.lockStore = lockStore;
    }

    public ItemStack createKey(LockData lock) {
        ItemStack item = new ItemStack(plugin.runtimeConfig().keyMaterial());
        item.editMeta(meta -> {
            meta.displayName(Component.text("ChestLock Key", NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("Opens one bound container", NamedTextColor.GRAY),
                    Component.text("Owner: " + lock.ownerName(), NamedTextColor.DARK_GRAY),
                    Component.text("Lock: " + lock.lockId().toString().substring(0, 8), NamedTextColor.DARK_GRAY),
                    Component.text("Transferable • does not permit breaking", NamedTextColor.YELLOW)
            ));
            meta.setEnchantmentGlintOverride(plugin.runtimeConfig().keyGlint());
            meta.getPersistentDataContainer().set(lockStore.itemLockIdKey(), PersistentDataType.STRING,
                    lock.lockId().toString());
            meta.getPersistentDataContainer().set(lockStore.itemKeyTokenKey(), PersistentDataType.STRING,
                    lock.keyToken().toString());
        });
        return item;
    }

    public boolean isValidKey(ItemStack item, LockData lock) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        String lockId = meta.getPersistentDataContainer().get(lockStore.itemLockIdKey(), PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(lockStore.itemKeyTokenKey(), PersistentDataType.STRING);
        return lock.lockId().toString().equals(lockId) && lock.keyToken().toString().equals(token);
    }

    public boolean isChestLockKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(lockStore.itemLockIdKey(), PersistentDataType.STRING);
    }

    public boolean playerHoldsValidKey(Player player, LockData lock) {
        if (!player.hasPermission("chestlock.key.use")) {
            return false;
        }
        return isValidKey(player.getInventory().getItemInMainHand(), lock)
                || isValidKey(player.getInventory().getItemInOffHand(), lock);
    }

    public boolean usedValidKey(Player player, EquipmentSlot hand, LockData lock) {
        if (!player.hasPermission("chestlock.key.use")) {
            return false;
        }
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        return isValidKey(item, lock);
    }

    public void give(Player player, LockData lock) {
        player.getScheduler().run(plugin, task -> {
            ItemStack key = createKey(lock);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(key);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                plugin.feedback().message(player, "key-inventory-full");
            }
        }, null);
    }
}
