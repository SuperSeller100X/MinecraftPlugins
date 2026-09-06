package dev.superseller.rapidhoppers.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.superseller.rapidhoppers.RapidHoppersPlugin;
import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.config.Settings.ContainerType;
import dev.superseller.rapidhoppers.config.Settings.WorldMode;
import dev.superseller.rapidhoppers.engine.TransferMath;
import dev.superseller.rapidhoppers.util.Sounds;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The in-game control panel ({@code /rh gui}, {@code /rha gui}).
 *
 * <p>Admins can toggle the engine, tune the transfer interval, flip
 * container types and worlds, switch the throttle and reload the plugin.
 * Players with only {@code rapidhoppers.gui} see the same panel read-only.</p>
 */
public final class ControlPanel implements Listener {

    public static final int SLOT_ENGINE = 10;
    public static final int SLOT_SPEED = 13;
    public static final int SLOT_CONTAINERS = 16;
    public static final int SLOT_THROTTLE = 28;
    public static final int SLOT_LIMIT = 30;
    public static final int SLOT_WORLDS = 32;
    public static final int SLOT_STATS = 34;
    public static final int SLOT_RELOAD = 39;
    public static final int SLOT_CLOSE = 41;

    private final RapidHoppersPlugin plugin;

    public ControlPanel(RapidHoppersPlugin plugin) {
        this.plugin = plugin;
    }

    // --- opening ------------------------------------------------------------

    public void open(Player player, boolean readOnly) {
        Settings settings = plugin.settings();
        PanelHolder holder = new PanelHolder(readOnly);
        int size = settings.getGuiRows() * 9;
        Inventory inv = Bukkit.createInventory(holder, size,
                plugin.messages().deserialize(settings.getGuiTitle()));
        holder.setInventory(inv);
        render(inv, settings);
        player.openInventory(inv);
        plugin.sounds().play(player, Sounds.GUI_OPEN);
        if (readOnly) {
            plugin.messages().send(player, "gui.read-only");
        }
    }

    /** (Re)draws every item into the panel. */
    public void render(Inventory inv, Settings settings) {
        Material filler = material(settings.getGuiFiller(), null);
        if (filler != null) {
            ItemStack pane = icon(filler, Component.text(" "), List.of());
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, pane);
            }
        } else {
            inv.clear();
        }

        String state = stateTag(settings.isEnabled());
        set(inv, SLOT_ENGINE, settings.isEnabled() ? Material.HOPPER : Material.BARRIER,
                "gui.item.engine-name", "gui.item.engine-lore", Map.of("state", state));

        set(inv, SLOT_SPEED, Material.CLOCK, "gui.item.speed-name", "gui.item.speed-lore",
                Map.of("interval", String.valueOf(settings.getIntervalTicks()),
                        "speed", format(settings.speedFactor()),
                        "rate", format(settings.itemsPerSecond())));

        set(inv, SLOT_CONTAINERS, Material.MINECART, "gui.item.containers-name", "gui.item.containers-lore",
                Map.of("hopper", stateTag(settings.isContainerEnabled(ContainerType.HOPPER)),
                        "hopper_minecart", stateTag(settings.isContainerEnabled(ContainerType.HOPPER_MINECART))));

        set(inv, SLOT_THROTTLE, Material.REDSTONE_TORCH, "gui.item.throttle-name", "gui.item.throttle-lore",
                Map.of("state", stateTag(settings.isThrottleEnabled()),
                        "soft", format(settings.getThrottleSoftTps()),
                        "hard", format(settings.getThrottleHardTps()),
                        "tps", format(plugin.throttle().tps())));

        set(inv, SLOT_LIMIT, Material.IRON_BARS, "gui.item.limit-name", "gui.item.limit-lore",
                Map.of("limit", String.valueOf(settings.getMaxContainersPerChunk())));

        set(inv, SLOT_WORLDS, Material.MAP, "gui.item.worlds-name", "gui.item.worlds-lore",
                Map.of("mode", settings.getWorldMode().name(),
                        "count", String.valueOf(settings.getWorldList().size())));

        set(inv, SLOT_STATS, Material.PAPER, "gui.item.stats-name", "gui.item.stats-lore",
                Map.of("tracked", String.valueOf(plugin.stats().trackedContainers()),
                        "total", String.valueOf(plugin.stats().totalTransfers()),
                        "rate", format(plugin.stats().ratePerSecond()),
                        "tps", format(plugin.throttle().tps())));

        set(inv, SLOT_RELOAD, Material.COMPARATOR, "gui.item.reload-name", "gui.item.reload-lore", Map.of());
        set(inv, SLOT_CLOSE, Material.RED_STAINED_GLASS_PANE, "gui.item.close-name", "gui.item.close-lore", Map.of());
    }

    // --- interaction --------------------------------------------------------

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PanelHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PanelHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        Settings settings = plugin.settings();
        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) {
            plugin.sounds().play(player, Sounds.GUI_CLOSE);
            player.closeInventory();
            return;
        }
        if (slot == SLOT_STATS) {
            plugin.sounds().play(player, Sounds.GUI_CLICK);
            render(event.getInventory(), settings);
            return;
        }
        if (holder.isReadOnly() || !player.hasPermission("rapidhoppers.admin")) {
            plugin.sounds().play(player, Sounds.ERROR);
            plugin.messages().send(player, "general.no-permission");
            return;
        }

        ClickType click = event.getClick();
        boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;

        switch (slot) {
            case SLOT_ENGINE -> {
                settings.setEnabled(!settings.isEnabled());
                plugin.engine().refresh();
                plugin.sounds().playToggle(player, settings.isEnabled());
                plugin.configService().save();
            }
            case SLOT_SPEED -> {
                int delta = right ? 1 : -1;
                settings.setIntervalTicks(settings.getIntervalTicks() + delta);
                plugin.engine().refresh();
                plugin.sounds().play(player, Sounds.GUI_CLICK);
                plugin.configService().save();
            }
            case SLOT_CONTAINERS -> {
                ContainerType type = cycle();
                settings.setContainerEnabled(type, !settings.isContainerEnabled(type));
                plugin.sounds().playToggle(player, settings.isContainerEnabled(type));
                plugin.configService().save();
            }
            case SLOT_THROTTLE -> {
                settings.setThrottleEnabled(!settings.isThrottleEnabled());
                plugin.throttle().start();
                plugin.sounds().playToggle(player, settings.isThrottleEnabled());
                plugin.configService().save();
            }
            case SLOT_LIMIT -> {
                settings.setMaxContainersPerChunk(settings.getMaxContainersPerChunk() + (right ? -8 : 8));
                plugin.sounds().play(player, Sounds.GUI_CLICK);
                plugin.configService().save();
            }
            case SLOT_WORLDS -> {
                settings.setWorldMode(settings.getWorldMode() == WorldMode.BLACKLIST
                        ? WorldMode.WHITELIST : WorldMode.BLACKLIST);
                plugin.sounds().play(player, Sounds.GUI_CLICK);
                plugin.configService().save();
            }
            case SLOT_RELOAD -> {
                plugin.reloadAll();
                plugin.sounds().play(player, Sounds.SUCCESS);
            }
            default -> {
                return;
            }
        }
        render(event.getInventory(), plugin.settings());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PanelHolder
                && event.getPlayer() instanceof Player player) {
            plugin.sounds().play(player, Sounds.GUI_CLOSE);
        }
    }

    /** Refreshes every open panel; used by the GUI refresh task. */
    public void refreshOpenPanels() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PanelHolder) {
                render(top, plugin.settings());
            }
        }
    }

    // --- helpers ------------------------------------------------------------

    private ContainerType cycle() {
        ContainerType[] types = ContainerType.values();
        int index = plugin.nextContainerCursor(types.length);
        return types[index];
    }

    private void set(Inventory inv, int slot, Material material, String nameKey, String loreKey,
                     Map<String, String> placeholders) {
        if (slot >= inv.getSize()) {
            return;
        }
        Component name = plugin.messages().plain(nameKey, placeholders);
        List<Component> lore = new ArrayList<>(plugin.messages().lore(loreKey, placeholders));
        inv.setItem(slot, icon(material, name, lore));
    }

    private ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material != null ? material : fallback;
    }

    private String stateTag(boolean on) {
        return plugin.messages().raw(on ? "status.enabled" : "status.disabled");
    }

    private String format(double value) {
        return String.valueOf(TransferMath.round1(value));
    }
}
