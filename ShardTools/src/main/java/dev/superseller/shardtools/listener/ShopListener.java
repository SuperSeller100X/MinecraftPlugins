package dev.superseller.shardtools.listener;

import java.util.HashMap;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.command.Permissions;
import dev.superseller.shardtools.gui.ShopGui;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.util.Numbers;

/**
 * Handles clicks/drags in the shard shop and confirmation GUIs and runs
 * the purchases.
 */
public final class ShopListener implements Listener {

    private final ShardToolsPlugin plugin;

    public ShopListener(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof ShopGui.ShopHolder)) {
            if (topInv.getHolder() instanceof ShopGui.ConfirmHolder) {
                event.setCancelled(true);
                handleConfirmClick(event, (ShopGui.ConfirmHolder) topInv.getHolder());
            }
            return;
        }
        ShopGui.ShopHolder holder = (ShopGui.ShopHolder) topInv.getHolder();
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            return;
        }
        String id = holder.slotItems().get(event.getRawSlot());
        if (id == null) {
            return;
        }
        switch (id) {
            case "__prev__":
                plugin.gui().open(player, holder.page() - 1);
                return;
            case "__next__":
                plugin.gui().open(player, holder.page() + 1);
                return;
            default:
                break;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(id);
        if (entry == null) {
            return;
        }
        if (!player.hasPermission(Permissions.SHOP)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (plugin.settings().shopConfirm()) {
            plugin.gui().openConfirm(player, entry, holder.page());
            return;
        }
        purchase(player, entry, holder.page());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopGui.ShopHolder
                || top.getHolder() instanceof ShopGui.ConfirmHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleConfirmClick(InventoryClickEvent event, ShopGui.ConfirmHolder holder) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 15) {
            plugin.gui().open(player, holder.backPage());
            return;
        }
        if (slot != 11) {
            return;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(holder.itemId());
        if (entry == null) {
            return;
        }
        purchase(player, entry, holder.backPage());
    }

    private void purchase(Player player, ShardCatalog.Entry entry, int backPage) {
        Long price = plugin.priceBook().price(entry.id());
        if (price == null) {
            price = 0L;
        }
        Long newBalance = plugin.accounts().take(player.getUniqueId(), player.getName(), price);
        if (newBalance == null) {
            long balance = plugin.accounts().balance(player.getUniqueId(), player.getName());
            plugin.messages().send(player, "shop.insufficient",
                    "%price%", Numbers.format(price),
                    "%balance%", Numbers.format(balance),
                    "%symbol%", plugin.settings().symbol());
            denySound(player);
            return;
        }
        if (entry.isCommandItem()) {
            String command = entry.command().replace("%player%", player.getName());
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command);
        } else {
            give(player, plugin.items().create(entry, 1));
        }
        plugin.messages().send(player, "shop.purchased",
                "%item%", entry.displayName(),
                "%price%", Numbers.format(price),
                "%balance%", Numbers.format(newBalance),
                "%symbol%", plugin.settings().symbol());
        Sound sound = plugin.soundResolver().resolve(plugin.settings().soundPurchase());
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
        plugin.effects().equipEffect(player);
        plugin.accounts().saveAsync();
        plugin.gui().open(player, backPage);
    }

    private void give(Player player, ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    private void denySound(Player player) {
        Sound sound = plugin.soundResolver().resolve(plugin.settings().soundDeny());
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 0.9f);
        }
    }
}
