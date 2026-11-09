package dev.superseller.veltryx.spawner;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.shards.ShardService;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/** A separate shard shop for the seven permanent custom spawners. */
public final class SpawnerShopGui implements Listener {
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final ShardService shards;
    private final SpawnerItems items;
    private final SpawnerStore store;
    private final Messages messages;
    private final Logger logger;
    private final Consumer<Player> back;

    public SpawnerShopGui(ShardService shards, SpawnerItems items, SpawnerStore store,
                          Messages messages, Logger logger, Consumer<Player> back) {
        this.shards = shards;
        this.items = items;
        this.store = store;
        this.messages = messages;
        this.logger = logger;
        this.back = back;
    }

    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            if (!player.hasPermission("veltryx.shards")) {
                messages.send(player, "shop.no-permission", Map.of());
                return;
            }
            Holder holder = new Holder();
            Inventory inventory = Bukkit.createInventory(holder, 27, Txt.legacy("§8✦ §b§lSpawner shop"));
            holder.inventory = inventory;
            SpawnerType[] types = SpawnerType.values();
            for (int i = 0; i < types.length; i++) inventory.setItem(10 + i, items.icon(types[i]));
            inventory.setItem(4, named(Material.EMERALD, "§6Your shards: §b" + shards.balance(player.getUniqueId())));
            inventory.setItem(22, named(Material.ARROW, "§7Back to shard shop"));
            player.openInventory(inventory);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;
        if (!player.hasPermission("veltryx.shards")) {
            messages.send(player, "shop.no-permission", Map.of());
            return;
        }
        int slot = event.getSlot();
        if (slot == 22) { back.accept(player); return; }
        if (slot < 10 || slot >= 10 + SpawnerType.values().length) return;
        buy(player, SpawnerType.values()[slot - 10]);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private void buy(Player player, SpawnerType type) {
        if (player.getInventory().firstEmpty() < 0) {
            messages.send(player, "shards.shop-full", Map.of());
            return;
        }
        if (!shards.take(player.getUniqueId(), SpawnerType.PRICE_SHARDS)) {
            messages.send(player, "shards.shop-cannot-afford", Map.of(
                    "price", Long.toString(SpawnerType.PRICE_SHARDS),
                    "balance", Long.toString(shards.balance(player.getUniqueId()))));
            return;
        }
        SpawnerItems.Identity identity = new SpawnerItems.Identity(UUID.randomUUID(), type);
        boolean created = false;
        try {
            if (!store.create(identity)) throw new IllegalStateException("Could not save the new spawner");
            created = true;
            ItemStack item = items.create(identity);
            if (!player.getInventory().addItem(item).isEmpty()) {
                store.discardUnplaced(identity);
                shards.give(player.getUniqueId(), SpawnerType.PRICE_SHARDS);
                messages.send(player, "shards.shop-full", Map.of());
                return;
            }
        } catch (RuntimeException e) {
            if (created) store.discardUnplaced(identity);
            shards.give(player.getUniqueId(), SpawnerType.PRICE_SHARDS);
            logger.log(Level.SEVERE, "Could not purchase custom spawner", e);
            messages.send(player, "spawner.storage-error", Map.of());
            return;
        }
        messages.send(player, "shards.bought", Map.of("item", type.displayName() + " Spawner",
                "price", Long.toString(SpawnerType.PRICE_SHARDS)));
        open(player);
    }

    private static ItemStack named(Material type, String name) {
        ItemStack item = new ItemStack(type, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Txt.legacy(name)); item.setItemMeta(meta); }
        return item;
    }
}
