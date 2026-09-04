package dev.superseller.hourglass.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.GuiConfig;
import dev.superseller.hourglass.config.Messages;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Turns a configured {@link GuiConfig.Item} into an {@link ItemStack}: text and
 * lore come from {@code gui.yml} and are rendered through the same MiniMessage
 * pipeline as chat, so colours, gradients and hover text work everywhere.
 */
public final class GuiItems {

    private GuiItems() {
    }

    /** Builds one icon. {@code headOf} may be {@code null}. */
    public static ItemStack build(HourGlassPlugin plugin, GuiConfig.Item item, Map<String, String> placeholders,
                                  UUID headOf) {
        Material material = item.material() == null ? Material.STONE : item.material();
        boolean head = item.playerHead() && material == Material.PLAYER_HEAD && headOf != null;
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        Messages messages = plugin.messages();
        if (item.name() != null && !item.name().isBlank()) {
            meta.displayName(messages.render(item.name(), placeholders));
        }
        List<String> lore = item.lore();
        if (lore != null && !lore.isEmpty()) {
            meta.lore(messages.lore(lore, placeholders));
        }
        if (head && headOf != null) {
            applyHead(plugin, meta, headOf);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Points a skull at a player, tolerating unknown or offline ids. */
    private static void applyHead(HourGlassPlugin plugin, ItemMeta meta, UUID owner) {
        if (!(meta instanceof SkullMeta skull)) {
            return;
        }
        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
            skull.setOwningPlayer(offline);
        } catch (RuntimeException | LinkageError e) {
            if (plugin.config().debug()) {
                plugin.getLogger().fine("Skull for " + owner + " unavailable: " + e.getMessage());
            }
        }
    }

    /** The grey background pane. */
    public static ItemStack filler(GuiConfig.Screen screen) {
        ItemStack stack = new ItemStack(screen.fillerMaterial(), 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && screen.fillerName() != null && !screen.fillerName().isBlank()) {
            meta.displayName(net.kyori.adventure.text.Component.text(screen.fillerName()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** A plain icon built from a material and MiniMessage strings (used by tests/tools). */
    public static ItemStack simple(Material material, String name, List<net.kyori.adventure.text.Component> lore) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isBlank()) {
                meta.displayName(net.kyori.adventure.text.Component.text(name));
            }
            if (lore != null) {
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
