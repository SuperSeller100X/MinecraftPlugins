package dev.superseller.connectedtools.gui;

import dev.superseller.connectedtools.config.PluginSettings;
import dev.superseller.connectedtools.model.Connection;
import dev.superseller.connectedtools.model.ConnectionStore;
import dev.superseller.connectedtools.util.Colors;
import dev.superseller.connectedtools.util.ItemBuilder;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GuiManager {

    private final PluginSettings settings;
    private final ConnectionStore store;

    public GuiManager(PluginSettings settings, ConnectionStore store) {
        this.settings = settings;
        this.store = store;
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.MAIN, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, Colors.parse(settings.msg("gui.title").replace("{player}", player.getName())));
        holder.inventory(inv);

        List<Connection> connections = store.getConnections(player.getUniqueId());
        int slot = 0;
        for (Connection conn : connections) {
            if (slot >= 27) break;
            ItemStack icon = ItemBuilder.of(conn.getItemType(),
                    "&a" + conn.getItemName(),
                    "&7Target: &f" + conn.getTargetType(),
                    "&7Location: &f" + conn.getLocationString(),
                    settings.msg("gui.disconnect-lore"));
            inv.setItem(slot++, icon);
        }
        if (slot == 0) {
            inv.setItem(13, ItemBuilder.of(Material.BARRIER, settings.msg("gui.empty")));
        }
        player.openInventory(inv);
    }
}
