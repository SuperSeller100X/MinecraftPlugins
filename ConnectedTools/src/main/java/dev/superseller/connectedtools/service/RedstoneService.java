package dev.superseller.connectedtools.service;

import dev.superseller.connectedtools.ConnectedToolsPlugin;
import dev.superseller.connectedtools.model.Connection;
import dev.superseller.connectedtools.util.BlockUtil;
import dev.superseller.connectedtools.config.PluginSettings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class RedstoneService {

    private RedstoneService() {}

    public static void emitPulse(Connection connection, PluginSettings settings) {
        Location loc = connection.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        Block block = loc.getBlock();
        if (block == null) return;

        Material original = block.getType();
        boolean canToggle = BlockUtil.canToggle(original);

        if (canToggle) {
            boolean toggled = BlockUtil.toggleBlock(block, settings.pulseDurationTicks());
            if (toggled && original == Material.LEVER) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.lever-toggled").replace("{location}", connection.getLocationString()));
            } else if (toggled && original.name().endsWith("_BUTTON")) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.button-pressed").replace("{location}", connection.getLocationString()));
            } else if (toggled && original.name().endsWith("_DOOR")) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.door-toggled").replace("{location}", connection.getLocationString()));
            } else if (toggled && original == Material.REDSTONE_WIRE) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.wire-powered").replace("{location}", connection.getLocationString()));
            } else if (toggled && (original == Material.REDSTONE_TORCH || original == Material.REDSTONE_WALL_TORCH)) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.torch-toggled").replace("{location}", connection.getLocationString()));
            } else if (toggled && original == Material.OBSERVER) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.observer-triggered").replace("{location}", connection.getLocationString()));
            } else if (toggled && (original == Material.COMPARATOR || original == Material.REPEATER)) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg(original == Material.COMPARATOR ? "redstone.comparator-toggled" : "redstone.repeater-toggled").replace("{location}", connection.getLocationString()));
            } else if (toggled && original.name().endsWith("_TRAPDOOR")) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.trapdoor-toggled").replace("{location}", connection.getLocationString()));
            } else if (toggled && original.name().endsWith("_FENCE_GATE")) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.gate-toggled").replace("{location}", connection.getLocationString()));
            } else {
                org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
                if (p != null) p.sendMessage(settings.msg("redstone.generic-pulse")
                        .replace("{location}", connection.getLocationString())
                        .replace("{target}", original.name()));
            }
        } else {
            // For non-togglable blocks: place temporary redstone torch/block nearby
            emitAdjacentPulse(block, settings);
            org.bukkit.entity.Player p = Bukkit.getPlayer(connection.getOwner());
            if (p != null) p.sendMessage(settings.msg("redstone.generic-pulse")
                    .replace("{location}", connection.getLocationString())
                    .replace("{target}", original.name()));
        }
    }

    private static void emitAdjacentPulse(Block target, PluginSettings settings) {
        Block above = target.getLocation().add(0, 1, 0).getBlock();
        Material aboveOrig = above.getType();
        above.setType(Material.REDSTONE_BLOCK);
        Bukkit.getScheduler().runTaskLater(ConnectedToolsPlugin.getInstance(), () -> {
            if (target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) {
                above.setType(aboveOrig);
            }
        }, (long) settings.pulseDurationTicks());
    }
}
