package dev.superseller.swifttpa.gui;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.config.SwiftTpaConfig;
import dev.superseller.swifttpa.request.RequestType;
import dev.superseller.swifttpa.request.TeleportRequest;
import dev.superseller.swifttpa.scheduler.PlatformScheduler;
import dev.superseller.swifttpa.util.Sounds;
import dev.superseller.swifttpa.util.TimeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;

/**
 * The chest GUI that lists a player's pending teleport requests. Every
 * incoming request is a player head (skin texture loaded asynchronously) that
 * can be left-clicked to accept and right-clicked to deny. The bottom bar
 * holds the request toggle and a cancel button for the player's own outgoing
 * request.
 *
 * <p>All inventory work runs on the viewer's owning region thread. Clicks are
 * matched to requests through item persistent data (never by slot), so
 * re-renders cannot race a click into the wrong request.
 */
public final class RequestsGui {

    /** Inventory size: 45 content slots plus one bottom bar row. */
    public static final int SIZE = 54;

    /** Slot of the request toggle button. */
    public static final int TOGGLE_SLOT = 47;

    /** Slot of the outgoing-request cancel button. */
    public static final int CANCEL_SLOT = 51;

    private final SwiftTPAPlugin plugin;
    private final Set<UUID> openViewers = ConcurrentHashMap.newKeySet();
    private final NamespacedKey requestKey;

    public RequestsGui(SwiftTPAPlugin plugin) {
        this.plugin = plugin;
        this.requestKey = new NamespacedKey(plugin, "request_sender");
    }

    /** The PDC key that tags a head item with "senderUuid|senderName". */
    public NamespacedKey requestKey() {
        return requestKey;
    }

    /** Opens (or re-opens) the GUI for a player. Must run on the player's region thread. */
    public void open(Player viewer) {
        GuiHolder holder = new GuiHolder(viewer.getUniqueId());
        Component title = plugin.messages().deserialize(plugin.tpaConfig().guiTitle());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        openViewers.add(viewer.getUniqueId());
        render(viewer, inventory);
        viewer.openInventory(inventory);
        Sounds.play(plugin.tpaConfig(), viewer, "gui-open");
    }

    /** Called when the inventory is closed so refreshes stop targeting this viewer. */
    public void closed(Player viewer) {
        openViewers.remove(viewer.getUniqueId());
    }

    /** True while the player has the GUI open. */
    public boolean isOpen(UUID uuid) {
        return openViewers.contains(uuid);
    }

    /** Re-renders the GUI for one viewer if it is currently open. Safe from any thread. */
    public void refresh(UUID uuid) {
        if (!openViewers.contains(uuid)) {
            return;
        }
        Player viewer = Bukkit.getPlayer(uuid);
        if (viewer == null) {
            return;
        }
        PlatformScheduler.runForPlayer(viewer, () -> {
            if (!openViewers.contains(uuid)) {
                return;
            }
            Inventory top;
            try {
                top = viewer.getOpenInventory().getTopInventory();
            } catch (Throwable e) {
                return;
            }
            if (top.getHolder() instanceof GuiHolder holder && holder.viewer().equals(uuid)) {
                render(viewer, top);
            }
        });
    }

    /** Re-renders the GUI for every open viewer. Safe from any thread. */
    public void refreshAll() {
        for (UUID uuid : openViewers) {
            refresh(uuid);
        }
    }

    /** Full repaint of an open requests inventory. Must run on the viewer's region thread. */
    public void render(Player viewer, Inventory inventory) {
        SwiftTpaConfig config = plugin.tpaConfig();
        long now = System.currentTimeMillis();
        inventory.clear();

        List<TeleportRequest> incoming = plugin.service().store().incoming(viewer.getUniqueId());
        int slot = 0;
        for (int i = incoming.size() - 1; i >= 0 && slot < 45; i--) {
            inventory.setItem(slot++, headItem(viewer, incoming.get(i), now));
        }

        ItemStack filler = icon(config.guiIcon("filling", Material.GRAY_STAINED_GLASS_PANE), Component.empty());
        for (int bar = 45; bar < SIZE; bar++) {
            inventory.setItem(bar, filler);
        }

        boolean enabled = plugin.storage().data(viewer.getUniqueId()).requestsEnabled();
        inventory.setItem(TOGGLE_SLOT, toggleIcon(enabled));

        var outgoing = plugin.service().store().outgoing(viewer.getUniqueId());
        if (outgoing.isPresent()) {
            inventory.setItem(CANCEL_SLOT, cancelIcon(outgoing.get(), now));
        }
    }

    // ------------------------------------------------------------------ items

    private ItemStack headItem(Player viewer, TeleportRequest request, long now) {
        UUID senderUuid = request.sender();
        String senderName = plugin.service().nameOf(senderUuid);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(senderUuid, senderName);
            meta.setOwnerProfile(profile);
        } catch (Throwable ignored) {
            // A nameless plain head is still a perfectly usable button.
        }
        decorateMeta(meta, senderName, request, now);
        meta.getPersistentDataContainer().set(requestKey, PersistentDataType.STRING,
                senderUuid + "|" + senderName);
        item.setItemMeta(meta);
        upgradeTexture(viewer, item, senderUuid);
        return item;
    }

    /** Applies name, lore and — when possible — the skin texture asynchronously. */
    private void upgradeTexture(Player viewer, ItemStack item, UUID senderUuid) {
        ItemMeta current = item.getItemMeta();
        if (!(current instanceof SkullMeta skull) || skull.getOwnerProfile() == null) {
            return;
        }
        PlayerProfile profile = skull.getOwnerProfile();
        java.util.concurrent.CompletableFuture<PlayerProfile> future;
        try {
            future = profile.update();
        } catch (Throwable e) {
            return; // Texture purely cosmetic — never break the GUI on it.
        }
        future.thenAccept(updated -> PlatformScheduler.runForPlayer(viewer, () -> {
            Inventory top = viewer.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof GuiHolder holder) || !holder.viewer().equals(viewer.getUniqueId())) {
                return;
            }
            for (int slot = 0; slot < 45; slot++) {
                ItemStack slotItem = top.getItem(slot);
                if (slotItem == null || slotItem.getType() != Material.PLAYER_HEAD) {
                    continue;
                }
                ItemMeta slotMeta = slotItem.getItemMeta();
                if (!(slotMeta instanceof SkullMeta slotSkull)) {
                    continue;
                }
                String tag = slotSkull.getPersistentDataContainer().get(requestKey, PersistentDataType.STRING);
                if (tag != null && tag.startsWith(senderUuid.toString())) {
                    slotSkull.setOwnerProfile(updated);
                    slotItem.setItemMeta(slotSkull);
                }
            }
        })).exceptionally(ex -> null);
    }

    private void decorateMeta(SkullMeta meta, String senderName, TeleportRequest request, long now) {
        boolean tpa = request.type() == RequestType.TPA;
        Map<String, String> placeholders = Map.of(
                "player", senderName,
                "expires", request.expiresAtMs() <= 0 ? "∞" : TimeParser.format(request.secondsLeft(now)),
                "age", TimeParser.format(request.ageSeconds(now)));
        meta.displayName(plugin.messages().componentNoPrefix(
                tpa ? "gui.head-name-tpa" : "gui.head-name-here", placeholders));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.messages().stringList(tpa ? "gui.head-lore-tpa" : "gui.head-lore-here")) {
            lore.add(plugin.messages().deserialize(line, placeholders));
        }
        meta.lore(lore.isEmpty() ? null : lore);
    }

    private ItemStack toggleIcon(boolean enabled) {
        SwiftTpaConfig config = plugin.tpaConfig();
        Material material = enabled
                ? config.guiIcon("toggle-on", Material.LIME_DYE)
                : config.guiIcon("toggle-off", Material.GRAY_DYE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.messages().componentNoPrefix(enabled ? "gui.toggle-on-name" : "gui.toggle-off-name"));
            List<Component> lore = new ArrayList<>();
            for (String line : plugin.messages().stringList(enabled ? "gui.toggle-on-lore" : "gui.toggle-off-lore")) {
                lore.add(plugin.messages().deserialize(line));
            }
            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack cancelIcon(TeleportRequest request, long now) {
        SwiftTpaConfig config = plugin.tpaConfig();
        ItemStack item = new ItemStack(config.guiIcon("outgoing-cancel", Material.BARRIER));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = Map.of(
                    "player", plugin.service().nameOf(request.target()),
                    "type", request.type().spyName(),
                    "expires", request.expiresAtMs() <= 0 ? "∞" : TimeParser.format(request.secondsLeft(now)));
            meta.displayName(plugin.messages().componentNoPrefix("gui.cancel-name"));
            List<Component> lore = new ArrayList<>();
            for (String line : plugin.messages().stringList("gui.cancel-lore")) {
                lore.add(plugin.messages().deserialize(line, placeholders));
            }
            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack icon(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
