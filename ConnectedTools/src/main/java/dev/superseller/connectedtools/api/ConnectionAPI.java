package dev.superseller.connectedtools.api;

import dev.superseller.connectedtools.ConnectedToolsPlugin;
import dev.superseller.connectedtools.model.Connection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class ConnectionAPI {

    private ConnectionAPI() {}

    public static List<Connection> getConnections(UUID player) {
        return ConnectedToolsPlugin.getInstance().getStore().getConnections(player);
    }

    public static boolean isBinding(UUID player) {
        return ConnectedToolsPlugin.getInstance().getStore().isBinding(player);
    }

    public static Connection getConnection(UUID player, ItemStack item) {
        return ConnectedToolsPlugin.getInstance().getStore().getConnection(player, item);
    }

    public static void addConnection(UUID player, ItemStack item, org.bukkit.Location target) {
        ConnectedToolsPlugin.getInstance().getStore().addConnection(
                player, item, new Connection(player, item.getType().name(), item.getType(), target));
    }

    public static void removeConnection(UUID player, ItemStack item) {
        ConnectedToolsPlugin.getInstance().getStore().removeConnection(player, item);
    }
}
