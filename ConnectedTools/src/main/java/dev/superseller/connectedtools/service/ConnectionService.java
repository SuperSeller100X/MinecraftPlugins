package dev.superseller.connectedtools.service;

import dev.superseller.connectedtools.config.PluginSettings;
import dev.superseller.connectedtools.model.Connection;
import dev.superseller.connectedtools.model.ConnectionStore;
import dev.superseller.connectedtools.util.BlockUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class ConnectionService {

    private final JavaPlugin plugin;
    private final ConnectionStore store;
    private final PluginSettings settings;

    public ConnectionService(JavaPlugin plugin, ConnectionStore store, PluginSettings settings) {
        this.plugin = plugin;
        this.store = store;
        this.settings = settings;
    }

    public boolean enterBindingMode(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        store.setBindingPlayer(player.getUniqueId());
        player.sendMessage(settings.msg("binding.active").replace("{distance}", String.valueOf(settings.maxBindDistance())));
        return true;
    }

    public boolean bind(Player player, ItemStack item, org.bukkit.block.Block clicked) {
        if (!store.isBinding(player.getUniqueId())) {
            return false;
        }
        double dist = player.getLocation().distance(clicked.getLocation());
        if (dist > settings.maxBindDistance()) {
            player.sendMessage(settings.msg("binding.too-far").replace("{distance}", String.valueOf(settings.maxBindDistance())));
            return false;
        }
        Connection conn = new Connection(player.getUniqueId(), item.getType().name(), item.getType(), clicked.getLocation());
        store.addConnection(player.getUniqueId(), item, conn);
        store.clearBinding(player.getUniqueId());
        player.sendMessage(settings.msg("binding.bound")
                .replace("{item}", item.getType().name())
                .replace("{target}", clicked.getType().name())
                .replace("{location}", conn.getLocationString()));
        return true;
    }

    public boolean disconnect(Player player, ItemStack item) {
        Connection conn = store.getConnection(player.getUniqueId(), item);
        if (conn == null) {
            player.sendMessage(settings.msg("disconnect.none"));
            return false;
        }
        store.removeConnection(player.getUniqueId(), item);
        player.sendMessage(settings.msg("disconnect.success")
                .replace("{item}", item.getType().name())
                .replace("{location}", conn.getLocationString())
                .replace("{target}", conn.getTargetType()));
        return true;
    }

    public boolean emit(Player player, ItemStack item) {
        Connection conn = store.getConnection(player.getUniqueId(), item);
        if (conn == null) {
            player.sendMessage(settings.msg("emit.no-connection"));
            return false;
        }
        RedstoneService.emitPulse(conn, settings);
        return true;
    }

    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission("connectedtools.all") || player.hasPermission(permission);
    }
}
