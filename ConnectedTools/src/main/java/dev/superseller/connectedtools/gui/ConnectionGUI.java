package dev.superseller.connectedtools.gui;

import dev.superseller.connectedtools.ConnectedToolsPlugin;
import dev.superseller.connectedtools.model.Connection;
import dev.superseller.connectedtools.model.ConnectionStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ConnectionGUI {

    private final Player player;
    private final ConnectionStore store;

    public ConnectionGUI(Player player, ConnectionStore store) {
        this.player = player;
        this.store = store;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Connected Tools — " + player.getName());

        List<Connection> connections = store.getConnections(player.getUniqueId());
        int slot = 0;
        for (Connection conn : connections) {
            if (slot >= 27) break;

            ItemStack icon = new ItemStack(conn.getItemType());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + conn.getItemName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Target: " + conn.getTargetType());
            lore.add(ChatColor.GRAY + "Location: " + conn.getLocationString());
            lore.add(ChatColor.YELLOW + "Click to disconnect.");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
        }

        // Fill empty slots with filler
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = slot; i < 27; i++) {
            inv.setItem(i, filler);
        }

        player.openInventory(inv);
    }

    public static boolean handleClick(Player player, int slot) {
        ConnectionStore store = ConnectedToolsPlugin.getInstance().getStore();
        List<Connection> connections = store.getConnections(player.getUniqueId());
        if (slot < 0 || slot >= connections.size()) return false;
        Connection conn = connections.get(slot);
        String targetLocation = conn.getSerializedLocation();
        // Find matching entry by serialized location and remove
        java.util.Map<java.util.UUID, java.util.Map<String, Connection>> internalData = store.getData();
        java.util.Map<String, Connection> playerData = internalData.getOrDefault(player.getUniqueId(), new java.util.HashMap<>());
        String keyToRemove = null;
        for (java.util.Map.Entry<String, Connection> entry : new java.util.HashMap<>(playerData).entrySet()) {
            if (entry.getValue().getSerializedLocation().equals(targetLocation)) {
                keyToRemove = entry.getKey();
                break;
            }
        }
        if (keyToRemove != null) {
            playerData.remove(keyToRemove);
        }
        player.sendMessage(ChatColor.GREEN + "Disconnected: " + conn.getItemName());
        player.closeInventory();
        return true;
    }
}
