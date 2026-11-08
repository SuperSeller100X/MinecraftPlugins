package dev.superseller.veltryx.spawner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/** Twenty pages of generated loot, with a mode switch on every page. */
public final class SpawnerLootGui implements Listener {
    public static final class Holder implements InventoryHolder {
        private final Player viewer;
        private final SpawnerItems.Identity identity;
        private final SpawnerStore.Position position;
        private final int page;
        private Inventory inventory;
        Holder(Player viewer, SpawnerItems.Identity identity, SpawnerStore.Position position, int page) {
            this.viewer = viewer; this.identity = identity; this.position = position; this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final SpawnerStore store;
    private final Messages messages;
    private final Map<UUID, Set<Holder>> viewers = new ConcurrentHashMap<>();

    public SpawnerLootGui(SpawnerStore store, Messages messages) { this.store = store; this.messages = messages; }

    public void open(Player player, SpawnerItems.Identity identity, SpawnerStore.Position position, int page) {
        if (page < 0 || page >= SpawnerStore.PAGES) return;
        PlatformScheduler.runForPlayer(player, () -> {
            SpawnerStore.Page data = store.page(identity, position, page);
            if (data == null) { messages.send(player, "spawner.unavailable", Map.of()); return; }
            Holder holder = new Holder(player, identity, position, page);
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy("§8" + data.type().displayName() + " Spawner §7(" + (page + 1) + "/20)"));
            holder.inventory = inventory;
            render(holder, data);
            player.openInventory(inventory);
            viewers.compute(identity.id(), (id, existing) -> {
                Set<Holder> set = existing == null ? ConcurrentHashMap.newKeySet() : existing;
                set.add(holder);
                return set;
            });
        });
    }

    private void render(Holder holder, SpawnerStore.Page data) {
        for (int i = 0; i < SpawnerStore.PAGE_SIZE; i++) holder.inventory.setItem(i, data.items()[i]);
        for (int i = 45; i < 54; i++) holder.inventory.setItem(i,
                named(Material.GRAY_STAINED_GLASS_PANE, "§8 ", List.of()));
        if (holder.page > 0) holder.inventory.setItem(45, named(Material.ARROW, "§bPrevious page", List.of()));
        holder.inventory.setItem(47, named(Material.BOOK, "§bPage " + (holder.page + 1) + " / 20",
                List.of("§7Click a stack to collect it.", "§7Right-click to collect one item.")));
        String current = data.mode() == SpawnerStore.Mode.SPAWNER ? "Spawner" : "Inventory";
        String next = data.mode() == SpawnerStore.Mode.SPAWNER ? "Inventory" : "Spawner";
        holder.inventory.setItem(49, named(Material.LEVER, "§aMode: §f" + current,
                List.of("§7Spawner: creates mobs normally.", "§7Inventory: stores their loot-table drops.",
                        "§eClick to switch to " + next + " mode.")));
        if (holder.page < SpawnerStore.PAGES - 1) holder.inventory.setItem(53, named(Material.ARROW, "§bNext page", List.of()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;
        int slot = event.getSlot();
        if (slot == 45 && holder.page > 0) { open(player, holder.identity, holder.position, holder.page - 1); return; }
        if (slot == 53 && holder.page < SpawnerStore.PAGES - 1) { open(player, holder.identity, holder.position, holder.page + 1); return; }
        if (slot == 49) {
            SpawnerStore.Mode mode = store.toggle(holder.identity, holder.position);
            if (mode == null) messages.send(player, "spawner.unavailable", Map.of());
            else messages.send(player, "spawner.mode", Map.of("mode", mode == SpawnerStore.Mode.SPAWNER ? "Spawner" : "Inventory"));
            refreshViewers(holder.identity.id());
            return;
        }
        if (slot < 0 || slot >= SpawnerStore.PAGE_SIZE) return;
        SpawnerStore.Withdrawal result = store.withdraw(holder.identity, holder.position,
                holder.page * SpawnerStore.PAGE_SIZE + slot, holder.inventory.getItem(slot),
                event.isRightClick() && !event.isShiftClick(), player.getInventory());
        if (result.status() == SpawnerStore.WithdrawStatus.FULL) messages.send(player, "spawner.inventory-full", Map.of());
        else if (result.status() == SpawnerStore.WithdrawStatus.ERROR) messages.send(player, "spawner.storage-error", Map.of());
        else if (result.status() == SpawnerStore.WithdrawStatus.INVALID) messages.send(player, "spawner.unavailable", Map.of());
        refreshViewers(holder.identity.id());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        viewers.computeIfPresent(holder.identity.id(), (id, set) -> {
            set.remove(holder);
            return set.isEmpty() ? null : set;
        });
    }

    /** Updates viewers on their own entity threads, including after loot is generated. */
    public void refreshViewers(UUID id) {
        Set<Holder> current = viewers.get(id);
        if (current == null) return;
        for (Holder holder : current) {
            PlatformScheduler.runForPlayer(holder.viewer, () -> {
                if (holder.viewer.getOpenInventory().getTopInventory() != holder.inventory) return;
                SpawnerStore.Page data = store.page(holder.identity, holder.position, holder.page);
                if (data == null) {
                    holder.viewer.closeInventory();
                    messages.send(holder.viewer, "spawner.unavailable", Map.of());
                } else render(holder, data);
            });
        }
    }

    private static ItemStack named(Material type, String name, List<String> lore) {
        ItemStack item = new ItemStack(type, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Txt.legacy(name)); meta.lore(Txt.legacy(lore)); item.setItemMeta(meta); }
        return item;
    }
}
