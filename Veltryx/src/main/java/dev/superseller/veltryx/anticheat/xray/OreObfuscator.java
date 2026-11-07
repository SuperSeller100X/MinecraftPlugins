package dev.superseller.veltryx.anticheat.xray;

import dev.superseller.veltryx.config.VeltryxConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * Anti-xray block obfuscation.
 *
 * <p>Ores buried in solid rock (not visible from any air block) are sent to
 * nearby clients as the surrounding stone type. X-Ray texture packs and ESP
 * overlays therefore show plain stone. When a player digs towards the hidden
 * ore and exposes it, the real block is revealed to that client just in time.
 *
 * <p>This is client-side only ({@code sendBlockChange}); the real world is
 * never modified. Combine with Paper's built-in anti-xray for belt and braces.
 */
public final class OreObfuscator {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final VeltryxConfig config;
    private final Map<UUID, Long> lastScanMillis = new ConcurrentHashMap<>();

    public OreObfuscator(VeltryxConfig config) {
        this.config = config;
    }

    /** Whether the material is an ore worth hiding. */
    public static boolean isOre(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    /** The fake block shown in place of a hidden ore. */
    public static Material fakeFor(Material ore) {
        return switch (ore) {
            case DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_COPPER_ORE,
                 DEEPSLATE_GOLD_ORE, DEEPSLATE_REDSTONE_ORE, DEEPSLATE_EMERALD_ORE,
                 DEEPSLATE_LAPIS_ORE, DEEPSLATE_DIAMOND_ORE -> Material.DEEPSLATE;
            case NETHER_GOLD_ORE, NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> Material.NETHERRACK;
            default -> Material.STONE;
        };
    }

    /**
     * Counts how many of the six neighbouring faces are exposed (non-solid).
     * 0 = fully buried, 6 = floating in air.
     */
    public static int exposedFaces(Block block) {
        int exposed = 0;
        for (BlockFace face : FACES) {
            if (!block.getRelative(face).getType().isSolid()) {
                exposed++;
            }
        }
        return exposed;
    }

    /** Whether every neighbour is solid — the ore is invisible from anywhere. */
    public static boolean isHidden(Block block) {
        return exposedFaces(block) == 0;
    }

    /**
     * Scans the blocks around the player and fakes any hidden ore. Throttled
     * per player by {@code anticheat.xray.update-interval-ms}. Safe to call on
     * every move event.
     */
    public void scanAround(Player player) {
        if (!config.xrayObfuscation()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastScanMillis.get(player.getUniqueId());
        if (last != null && now - last < config.xrayUpdateIntervalMs()) {
            return;
        }
        lastScanMillis.put(player.getUniqueId(), now);

        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int radius = config.xrayRadius();
        int minY = Math.max(config.xrayScanYMin(), world.getMinHeight());
        int maxY = Math.min(config.xrayScanYMax(), world.getMaxHeight() - 1);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        List<Map.Entry<Location, org.bukkit.block.data.BlockData>> fakes = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int y = cy + dy;
                    if (y < minY || y > maxY) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    if (!isOre(block.getType()) || !isHidden(block)) {
                        continue;
                    }
                    Location location = block.getLocation();
                    fakes.add(Map.entry(location, fakeFor(block.getType()).createBlockData()));
                }
            }
        }
        for (Map.Entry<Location, org.bukkit.block.data.BlockData> fake : fakes) {
            player.sendBlockChange(fake.getKey(), fake.getValue());
        }
    }

    /**
     * Reveals the real state of hidden ores around a block to one player
     * (call when the player digs/interacts near them).
     */
    public void revealAround(Player player, Location center, int radius) {
        if (!config.xrayObfuscation() || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int y = cy + dy;
                    if (y < minY || y > maxY) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    if (isOre(block.getType())) {
                        player.sendBlockChange(block.getLocation(), block.getBlockData());
                    }
                }
            }
        }
    }

    /** Forgets the throttle state of a leaving player. */
    public void forget(UUID uuid) {
        lastScanMillis.remove(uuid);
    }
}
