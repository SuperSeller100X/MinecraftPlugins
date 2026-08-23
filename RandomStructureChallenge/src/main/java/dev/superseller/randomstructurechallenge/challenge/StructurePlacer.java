package dev.superseller.randomstructurechallenge.challenge;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;
import dev.superseller.randomstructurechallenge.config.PluginSettings;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Places a vanilla worldgen structure the same way {@code /place structure} does.
 */
public final class StructurePlacer {

    private final RandomStructureChallengePlugin plugin;
    private final PluginSettings settings;
    private final StructureCatalog catalog;
    private final Logger logger;

    public StructurePlacer(
            RandomStructureChallengePlugin plugin,
            PluginSettings settings,
            StructureCatalog catalog) {
        this.plugin = plugin;
        this.settings = settings;
        this.catalog = catalog;
        this.logger = plugin.getLogger();
    }

    public boolean eligible(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (!settings.includeSpectators() && player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        World world = player.getWorld();
        return world != null && settings.worldAllowed(world.getName());
    }

    /**
     * Tries several random structures until vanilla placement succeeds.
     *
     * @return the structure key that was placed, or {@code null}
     */
    public String placeOn(Player player, String preferred) {
        if (!eligible(player)) {
            return null;
        }
        List<String> order = catalog.shuffled();
        if (preferred != null && !preferred.isBlank()) {
            order.remove(preferred);
            order.add(0, preferred);
        }
        int attempts = Math.min(settings.placementRetries(), order.size());
        for (int i = 0; i < attempts; i++) {
            String key = order.get(i);
            if (placeOnce(player, key)) {
                if (settings.safePocket()) {
                    SafePocket.ensure(player);
                }
                return key;
            }
        }
        logger.warning("Failed to place a structure on " + player.getName()
                + " after " + attempts + " attempt(s).");
        return null;
    }

    private boolean placeOnce(Player player, String structureKey) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        loadChunks(world, loc);
        String worldKey = worldKey(world);
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String primary = "minecraft:execute in " + worldKey
                + " run minecraft:place structure " + structureKey + " " + x + " " + y + " " + z;
        if (dispatch(primary)) {
            return true;
        }
        String fallback = "execute in " + worldKey
                + " run place structure " + structureKey + " " + x + " " + y + " " + z;
        return dispatch(fallback);
    }

    private boolean dispatch(String command) {
        try {
            CommandSender console = Bukkit.getConsoleSender();
            return Bukkit.dispatchCommand(console, command);
        } catch (Throwable t) {
            logger.warning("Could not dispatch `/place structure`: " + t.getMessage());
            return false;
        }
    }

    private void loadChunks(World world, Location loc) {
        int radius = settings.chunkRadius();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                try {
                    world.getChunkAt(cx + dx, cz + dz);
                } catch (Throwable ignored) {
                    // Folia may refuse chunks owned by another region.
                }
            }
        }
    }

    static String worldKey(World world) {
        try {
            var key = world.getKey();
            return key.getNamespace() + ":" + key.getKey();
        } catch (Throwable ignored) {
            String name = world.getName();
            if ("world".equalsIgnoreCase(name)) {
                return "minecraft:overworld";
            }
            if ("world_nether".equalsIgnoreCase(name)) {
                return "minecraft:the_nether";
            }
            if ("world_the_end".equalsIgnoreCase(name)) {
                return "minecraft:the_end";
            }
            return "minecraft:" + name.toLowerCase(Locale.ROOT);
        }
    }
}
