package dev.superseller.combattag.gui;

import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.CombatTagEntry;
import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.util.SoundUtil;
import dev.superseller.combattag.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Builds the player status GUI and the admin control panel. */
public final class CombatGui {

    private final PluginConfig config;
    private final Messages messages;
    private final CombatManager combat;

    public CombatGui(PluginConfig config, Messages messages, CombatManager combat) {
        this.config = config;
        this.messages = messages;
        this.combat = combat;
    }

    /** Opens the personal combat status screen. */
    public void openStatus(Player player) {
        CombatTagHolder holder = new CombatTagHolder(CombatTagHolder.Type.STATUS, player.getUniqueId());
        int size = config.getGuiRows() * 9;
        Inventory inv = Bukkit.createInventory(holder, size, messages.get("gui.title-status", Map.of()));
        holder.setInventory(inv);

        boolean tagged = combat.isTagged(player.getUniqueId());
        Optional<CombatTagEntry> entry = combat.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        String seconds = entry.map(e -> String.valueOf(e.remainingSeconds(now))).orElse("0");
        String pretty = entry.map(e -> TimeUtil.format(e.remainingMillis(now))).orElse("0.0s");
        String opponent = entry.map(CombatTagEntry::opponent)
                .map(o -> {
                    Player p = Bukkit.getPlayer(o);
                    return p != null ? p.getName() : "unknown";
                }).orElse("-");

        filler(inv);
        inv.setItem(center(size) - 2, item(
                tagged ? Material.NETHERITE_SWORD : Material.LIME_DYE,
                messages.get(tagged ? "gui.status.tagged-name" : "gui.status.safe-name", Map.of()),
                messages.getList(tagged ? "gui.status.tagged-lore" : "gui.status.safe-lore",
                        Map.of("seconds", seconds, "time", pretty, "opponent", opponent))));

        inv.setItem(center(size), item(Material.BOOK,
                messages.get("gui.status.restrictions-name", Map.of()),
                messages.getList("gui.status.restrictions-lore", Map.of(
                        "shops", onOff(config.isBlockShops()),
                        "teleports", onOff(config.isBlockTeleports()),
                        "easymending", onOff(config.isBlockEasyMending()),
                        "commands", onOff(config.isBlockedCommandsEnabled()),
                        "duration", String.valueOf(config.getTagSeconds())))));

        inv.setItem(center(size) + 2, item(Material.BARRIER,
                messages.get("gui.close-name", Map.of()),
                messages.getList("gui.close-lore", Map.of())));

        player.openInventory(inv);
        SoundUtil.play(player, config.getSoundGuiOpen(), config.isSoundsEnabled());
    }

    /** Opens the admin control panel. */
    public void openAdmin(Player player) {
        CombatTagHolder holder = new CombatTagHolder(CombatTagHolder.Type.ADMIN, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, messages.get("gui.title-admin", Map.of()));
        holder.setInventory(inv);
        filler(inv);

        inv.setItem(10, item(Material.CLOCK,
                messages.get("gui.admin.duration-name", Map.of("seconds", String.valueOf(config.getTagSeconds()))),
                messages.getList("gui.admin.duration-lore", Map.of("seconds", String.valueOf(config.getTagSeconds())))));

        inv.setItem(12, item(Material.PLAYER_HEAD,
                messages.get("gui.admin.active-name", Map.of("count", String.valueOf(combat.getActiveCount()))),
                messages.getList("gui.admin.active-lore", Map.of("count", String.valueOf(combat.getActiveCount())))));

        inv.setItem(14, item(Material.PAPER,
                messages.get("gui.admin.stats-name", Map.of()),
                messages.getList("gui.admin.stats-lore", Map.of(
                        "tags", String.valueOf(combat.getTotalTags()),
                        "blocked", String.valueOf(combat.getTotalBlocked()),
                        "logs", String.valueOf(combat.getTotalCombatLogs())))));

        inv.setItem(16, item(Material.TNT,
                messages.get("gui.admin.clear-name", Map.of()),
                messages.getList("gui.admin.clear-lore", Map.of())));

        inv.setItem(22, item(Material.COMPARATOR,
                messages.get("gui.admin.reload-name", Map.of()),
                messages.getList("gui.admin.reload-lore", Map.of())));

        player.openInventory(inv);
        SoundUtil.play(player, config.getSoundGuiOpen(), config.isSoundsEnabled());
    }

    private static int center(int size) {
        return size / 2;
    }

    private String onOff(boolean value) {
        return messages.getRaw(value ? "gui.toggle-on" : "gui.toggle-off");
    }

    private void filler(Inventory inv) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> cleaned = new ArrayList<>();
            for (Component line : lore) {
                cleaned.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(cleaned);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
