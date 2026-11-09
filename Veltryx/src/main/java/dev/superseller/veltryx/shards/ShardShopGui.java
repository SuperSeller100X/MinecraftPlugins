package dev.superseller.veltryx.shards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/**
 * The shard shop: buy the five shard items with shards. The pickaxe opens an
 * enchant picker (Fortune III or Silk Touch); the axe always has Silk Touch. Shards are earned by
 * playing (1/minute), paid between players or given by staff - there is
 * deliberately no way to buy shards with money.
 */
public final class ShardShopGui implements Listener {

    /** Marks the shard shop overview. */
    public static final class ShopHolder implements InventoryHolder {
        private final List<ShardCatalog.Entry> slots = new ArrayList<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Marks the pick-one enchant screen of one tool. */
    public static final class ChoiceHolder implements InventoryHolder {
        private final ShardCatalog.Entry entry;
        private Inventory inventory;

        ChoiceHolder(ShardCatalog.Entry entry) {
            this.entry = entry;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ShardCatalog catalog;
    private final ShardItems items;
    private final ShardService service;
    private final Messages messages;
    private java.util.function.Consumer<Player> spawnerOpener;

    public ShardShopGui(ShardCatalog catalog, ShardItems items, ShardService service, Messages messages) {
        this.catalog = catalog;
        this.items = items;
        this.service = service;
        this.messages = messages;
    }

    public void attachSpawnerOpener(java.util.function.Consumer<Player> opener) { this.spawnerOpener = opener; }

    public void openSpawnerShop(Player player) {
        if (spawnerOpener != null) spawnerOpener.accept(player);
    }

    /** Opens the shard shop (Folia-safe). */
    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            ShopHolder holder = new ShopHolder();
            Inventory inventory = Bukkit.createInventory(holder, 54, Txt.legacy("§8✦ §b§lShard shop"));
            holder.inventory = inventory;

            int[] slots = {10, 12, 14, 20, 22};
            List<ShardCatalog.Entry> entries = catalog.all();
            for (int i = 0; i < entries.size() && i < slots.length; i++) {
                ShardCatalog.Entry entry = entries.get(i);
                inventory.setItem(slots[i], items.displayIcon(entry, entry.priceShards()));
                holder.slots.add(entry);
            }
            inventory.setItem(24, item(Material.SPAWNER, 1,
                    Txt.legacy("§bSpawner shop"),
                    List.of(Txt.legacy("§7Seven §bpermanent §7spawners"),
                            Txt.legacy("§7Price: §61,000 shards §7each"), Txt.legacy("§eClick to browse"))));
            inventory.setItem(45, item(Material.PAPER, 1,
                    Txt.legacy("§bShards"),
                    List.of(Txt.legacy("§7Earned by playing: §f1 shard per minute"),
                            Txt.legacy("§7Pay friends with §f/shard pay§7."))));
            inventory.setItem(49, item(Material.EMERALD, 1,
                    Txt.legacy("§6Your shards: §b" + service.balance(player.getUniqueId())),
                    List.of(Txt.legacy("§7Check the top earners with §f/shard top§7."))));
            player.openInventory(inventory);
        });
    }

    /** Opens the Fortune III vs Silk Touch picker for one tool. */
    public void openChoice(Player player, ShardCatalog.Entry entry) {
        PlatformScheduler.runForPlayer(player, () -> {
            ChoiceHolder holder = new ChoiceHolder(entry);
            Inventory inventory = Bukkit.createInventory(holder, 27,
                    Txt.legacy("§8✦ §b" + entry.displayName() + " §7- pick an enchant"));
            holder.inventory = inventory;

            List<ShardCatalog.EnchantSpec> choices = entry.choice();
            if (choices.size() > 0) {
                inventory.setItem(11, item(Material.ENCHANTED_BOOK, 1,
                        Txt.legacy("§b" + choices.get(0).displayName()),
                        List.of(Txt.legacy("§eClick to buy with this enchant"))));
            }
            if (choices.size() > 1) {
                inventory.setItem(15, item(Material.BOOK, 1,
                        Txt.legacy("§b" + choices.get(1).displayName()),
                        List.of(Txt.legacy("§eClick to buy with this enchant"))));
            }
            inventory.setItem(22, item(Material.BARRIER, 1,
                    Txt.legacy("§7Back to the shop"), List.of()));
            player.openInventory(inventory);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof ShopHolder shop) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != shop) {
                return;
            }
            if (event.getSlot() == 24) {
                openSpawnerShop(player);
                return;
            }
            int index = indexOf(shop, event.getSlot());
            if (index < 0 || index >= shop.slots.size()) {
                return;
            }
            ShardCatalog.Entry entry = shop.slots.get(index);
            if (!items.available(entry)) {
                messages.send(player, "shop.item-unavailable", Map.of());
                return;
            }
            if (entry.hasChoice()) {
                openChoice(player, entry);
            } else {
                buy(player, entry, 0);
            }
            return;
        }
        if (holder instanceof ChoiceHolder choice) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != choice) {
                return;
            }
            int slot = event.getSlot();
            if (slot == 11 && choice.entry.hasChoice()) {
                buy(player, choice.entry, 0);
            } else if (slot == 15 && choice.entry.choice().size() > 1) {
                buy(player, choice.entry, 1);
            } else if (slot == 22) {
                open(player);
            }
        }
    }

    private int indexOf(ShopHolder shop, int slot) {
        int[] slots = {10, 12, 14, 20, 22};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /** Buys one catalog entry with the given enchant choice. */
    private void buy(Player player, ShardCatalog.Entry entry, int choiceIndex) {
        if (!items.available(entry)) {
            messages.send(player, "shop.item-unavailable", Map.of());
            return;
        }
        ItemStack item;
        try {
            item = items.create(entry, choiceIndex);
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger("Veltryx").log(java.util.logging.Level.WARNING,
                    "Could not create shard item " + entry.id(), ex);
            messages.send(player, "shop.item-unavailable", Map.of());
            return;
        }
        if (!service.take(player.getUniqueId(), entry.priceShards())) {
            messages.send(player, "shards.shop-cannot-afford",
                    Map.of("price", String.valueOf(entry.priceShards()),
                            "balance", String.valueOf(service.balance(player.getUniqueId()))));
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            service.give(player.getUniqueId(), entry.priceShards());
            messages.send(player, "shards.shop-full", Map.of());
            return;
        }
        player.playSound(player.getLocation(), "block.amethyst_block.resonate", 1.0f, 1.8f);
        var location = player.getLocation();
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, location.getX(), location.getY() + 1, location.getZ(),
                10, 0.35, 0.35, 0.35, 0.01);
        messages.send(player, "shards.bought",
                Map.of("item", entry.displayName(), "price", String.valueOf(entry.priceShards())));
        open(player);
    }

    private static ItemStack item(Material material, int amount, net.kyori.adventure.text.Component name,
                                  List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
