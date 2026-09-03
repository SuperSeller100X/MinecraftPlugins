package dev.superseller.combattag.listener;

import dev.superseller.combattag.CombatTagPlugin;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.gui.CombatGui;
import dev.superseller.combattag.gui.CombatTagHolder;
import dev.superseller.combattag.util.SoundUtil;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Makes CombatTag's GUIs read-only and handles admin panel buttons. */
public final class GuiListener implements Listener {

    private final CombatTagPlugin plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final CombatManager combat;
    private final CombatGui gui;

    public GuiListener(CombatTagPlugin plugin, PluginConfig config, Messages messages,
                       CombatManager combat, CombatGui gui) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.combat = combat;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CombatTagHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SoundUtil.play(player, config.getSoundGuiClick(), config.isSoundsEnabled());

        int slot = event.getRawSlot();
        if (holder.getType() == CombatTagHolder.Type.STATUS) {
            if (event.getCurrentItem() != null
                    && event.getCurrentItem().getType() == org.bukkit.Material.BARRIER) {
                player.closeInventory();
            }
            return;
        }

        if (!player.hasPermission("combattag.admin")) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 16 -> {
                int cleared = combat.untagAll();
                messages.send(player, "admin.cleared", "count", String.valueOf(cleared));
                gui.openAdmin(player);
            }
            case 20 -> {
                if (!player.hasPermission("combattag.admin.bypass")) {
                    messages.send(player, "error.no-permission");
                    return;
                }
                boolean newValue = config.setBypassEnabled(!config.isBypassEnabled());
                messages.send(player, "admin.bypass-master",
                        "state", messages.getRaw(newValue ? "admin.bypass-state-on" : "admin.bypass-state-off"),
                        "effect", messages.getRaw(newValue
                                ? "admin.bypass-master-on-effect"
                                : "admin.bypass-master-off-effect"));
                gui.openAdmin(player);
            }
            case 22 -> {
                plugin.reloadEverything();
                messages.send(player, "admin.reloaded");
                gui.openAdmin(player);
            }
            case 10 -> messages.send(player, "admin.duration-hint",
                    "seconds", String.valueOf(config.getTagSeconds()));
            case 12, 14 -> player.sendMessage(messages.get("admin.stats", Map.of(
                    "active", String.valueOf(combat.getActiveCount()),
                    "tags", String.valueOf(combat.getTotalTags()),
                    "blocked", String.valueOf(combat.getTotalBlocked()),
                    "logs", String.valueOf(combat.getTotalCombatLogs()))));
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CombatTagHolder) {
            event.setCancelled(true);
        }
    }
}
