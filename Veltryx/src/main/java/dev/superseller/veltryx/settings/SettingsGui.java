package dev.superseller.veltryx.settings;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/** /settings: independent, persisted player preferences. */
public final class SettingsGui implements Listener, CommandExecutor, TabCompleter {
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private final PreferencesStore store;
    private final SettingsListener settings;
    private final Messages messages;

    public SettingsGui(PreferencesStore store, SettingsListener settings, Messages messages) {
        this.store = store; this.settings = settings; this.messages = messages;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(messages.render("general.players-only")); return true; }
        if (!player.hasPermission("veltryx.settings")) { messages.send(player, "general.no-permission", Map.of()); return true; }
        open(player); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            PreferencesStore.Preferences p = store.get(player.getUniqueId());
            Holder h = new Holder();
            Inventory inv = Bukkit.createInventory(h, 27, Txt.legacy("§8Settings"));
            h.inventory = inv;
            for (int i = 0; i < 27; i++) inv.setItem(i, icon("GRAY_STAINED_GLASS_PANE", "§7 ", List.of()));
            inv.setItem(11, icon("ZOMBIE_HEAD", "§bNatural mob spawning: " + state(!p.mobsDisabled()),
                    List.of("§7Toggle natural spawns attributed to you.",
                            "§7Other players may still spawn mobs nearby.", "§eClick to toggle")));
            inv.setItem(13, icon("PHANTOM_MEMBRANE", "§bPhantom spawning: " + state(!p.phantomsDisabled() && !p.mobsDisabled()),
                    List.of("§7Your phantom preference: " + state(!p.phantomsDisabled()),
                            "§7Disabling all mobs also stops phantoms.", "§eClick to toggle")));
            inv.setItem(15, icon("ENDER_EYE", "§bNight vision: " + state(p.nightVision()),
                    List.of("§7Permanent night vision while enabled.", "§eClick to toggle")));
            player.openInventory(inv);
        });
    }
    private static String state(boolean on) { return on ? "§aON" : "§cOFF"; }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player) || e.getClickedInventory() == null
                || e.getClickedInventory().getHolder() != h) return;
        PreferencesStore.Preferences p = store.get(player.getUniqueId());
        String setting;
        String enabled;
        switch (e.getSlot()) {
            case 11 -> { p = p.toggleMobs(); setting = "Natural mob spawning"; enabled = p.mobsDisabled() ? "OFF" : "ON"; }
            case 13 -> {
                p = p.togglePhantoms(); setting = "Phantom spawning";
                enabled = p.phantomsDisabled() ? "OFF" : p.mobsDisabled() ? "ON (when natural mob spawning is enabled)" : "ON";
            }
            case 15 -> { p = p.toggleNightVision(); setting = "Night vision"; enabled = p.nightVision() ? "ON" : "OFF"; }
            default -> { return; }
        }
        store.set(player.getUniqueId(), p);
        settings.updateNightVision(player);
        messages.send(player, "settings.updated", Map.of("setting", setting, "state", enabled));
        open(player);
    }
    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
    private static ItemStack icon(String material, String name, List<String> lore) {
        Material type = Material.matchMaterial(material);
        ItemStack stack = new ItemStack(type == null ? Material.PAPER : type);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) { meta.displayName(Txt.legacy(name)); meta.lore(Txt.legacy(lore)); stack.setItemMeta(meta); }
        return stack;
    }
}
