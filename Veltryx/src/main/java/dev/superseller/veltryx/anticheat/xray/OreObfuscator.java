package dev.superseller.veltryx.anticheat.xray;

import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.util.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Anti-xray block obfuscation. Client-side only — the real world is never
 * modified.
 *
 * <p>Every ore in the configured y-range is sent to clients as the surrounding
 * stone type the moment the client receives the chunk (the
 * {@code PlayerChunkLoadEvent} hook), so X-Ray packs and ESP overlays see plain
 * stone with no visible flash: the fake blocks are queued right behind the
 * chunk packet itself and re-sent once two ticks later to beat any packet
 * split. Chunk re-receives (client cache drops, teleports, relogs) fire the
 * same event and are therefore covered exactly.
 *
 * <p>Ore becomes visible again when a player is close enough to legitimately
 * see it ({@code reveal-radius}) or digs/interacts near it. Revealed positions
 * are remembered per player and never re-hidden during the session, so nothing
 * flickers. A throttled near-area sweep re-fakes anything that leaked and
 * reveals what is legitimately in sight.
 */
public final class OreObfuscator {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    /** One hidden ore block: position, real type and the fake sent to clients. */
    private record Hidden(int x, int y, int z, Material real, BlockData fake) {
    }

    /** Per-player client-side state. */
    private static final class PlayerState {
        final Set<Long> revealed = ConcurrentHashMap.newKeySet();
        long lastSweepMillis;
    }

    private final VeltryxConfig config;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    /** world -> chunk key -> hidden ores in that chunk (computed once). */
    private final Map<World, Map<Long, List<Hidden>>> index = new ConcurrentHashMap<>();

    public OreObfuscator(VeltryxConfig config) {
        this.config = config;
    }

    // ------------------------------------------------------------ pure API

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

    /** Packs a block position into one long key. */
    public static long packBlock(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((y & 0xFFFL) << 26) | (z & 0x3FFFFFFL);
    }

    /** Packs a chunk coordinate into one long key. */
    public static long packChunk(int x, int z) {
        return ((x & 0xFFFFFFFFL) << 32) | (z & 0xFFFFFFFFL);
    }

    // ---------------------------------------------------------- entry points

    /**
     * Called when a player's client receives a chunk: all hidden ore in it is
     * faked immediately (plus one safety re-send two ticks later).
     */
    public void chunkSent(Player player, World world, int cx, int cz) {
        if (!config.xrayObfuscation()) {
            return;
        }
        List<Hidden> hidden = hiddenIn(world, cx, cz);
        patchChunk(player, world, hidden, stateFor(player));
        PlatformScheduler.runLater(() -> {
            if (player.isOnline()) {
                patchChunk(player, world, hidden, stateFor(player));
            }
        }, 2L);
    }

    /** Forces an immediate sweep (join / teleport / respawn). */
    public void scanNow(Player player) {
        if (!config.xrayObfuscation()) {
            return;
        }
        stateFor(player).lastSweepMillis = 0;
        scanAround(player);
    }

    /**
     * Throttled near-area sweep: ore within {@code reveal-radius} of the
     * player is shown real (legitimately visible), everything else nearby is
     * re-faked to catch any leak.
     */
    public void scanAround(Player player) {
        if (!config.xrayObfuscation()) {
            return;
        }
        PlayerState state = stateFor(player);
        long now = System.currentTimeMillis();
        if (now - state.lastSweepMillis < config.xrayUpdateIntervalMs()) {
            return;
        }
        state.lastSweepMillis = now;

        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int reveal = config.xrayRevealRadius();
        int sweep = Math.max(reveal + 2, config.xraySweepRadius());
        double revealSq = (double) reveal * reveal;
        double sweepSq = (double) sweep * sweep;
        double px = center.getX();
        double py = center.getY();
        double pz = center.getZ();

        int pcx = center.getBlockX() >> 4;
        int pcz = center.getBlockZ() >> 4;
        int chunkRange = (sweep >> 4) + 1;
        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                if (!world.isChunkLoaded(pcx + dx, pcz + dz)) {
                    continue;
                }
                for (Hidden hidden : hiddenIn(world, pcx + dx, pcz + dz)) {
                    long key = packBlock(hidden.x(), hidden.y(), hidden.z());
                    double ddx = hidden.x() + 0.5 - px;
                    double ddy = hidden.y() + 0.5 - py;
                    double ddz = hidden.z() + 0.5 - pz;
                    double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (distSq <= revealSq) {
                        if (state.revealed.add(key)) {
                            sendReal(player, world, hidden);
                        }
                    } else if (distSq <= sweepSq && !state.revealed.contains(key)) {
                        sendFake(player, world, hidden);
                    }
                }
            }
        }
    }

    /**
     * Reveals the real state of hidden ore around a block to one player (call
     * when the player digs/interacts near them) and remembers the reveal so
     * nothing flickers afterwards.
     */
    public void revealAround(Player player, Location center, int radius) {
        if (!config.xrayObfuscation() || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        PlayerState state = stateFor(player);
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int range = (radius >> 4) + 1;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (!world.isChunkLoaded(cx + dx, cz + dz)) {
                    continue;
                }
                for (Hidden hidden : hiddenIn(world, cx + dx, cz + dz)) {
                    if (Math.abs(hidden.x() - center.getBlockX()) > radius
                            || Math.abs(hidden.y() - center.getBlockY()) > radius
                            || Math.abs(hidden.z() - center.getBlockZ()) > radius) {
                        continue;
                    }
                    state.revealed.add(packBlock(hidden.x(), hidden.y(), hidden.z()));
                    sendReal(player, world, hidden);
                }
            }
        }
    }

    /** Forgets the state of a leaving player. */
    public void forget(UUID uuid) {
        states.remove(uuid);
    }

    // ------------------------------------------------------------ internals

    private PlayerState stateFor(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new PlayerState());
    }

    private void patchChunk(Player player, World world, List<Hidden> hidden, PlayerState state) {
        for (Hidden ore : hidden) {
            if (!state.revealed.contains(packBlock(ore.x(), ore.y(), ore.z()))) {
                sendFake(player, world, ore);
            }
        }
    }

    private void sendFake(Player player, World world, Hidden ore) {
        Block block = world.getBlockAt(ore.x(), ore.y(), ore.z());
        if (block.getType() != ore.real()) {
            invalidateChunk(world, ore.x(), ore.z());
            return;
        }
        player.sendBlockChange(block.getLocation(), ore.fake());
    }

    private void sendReal(Player player, World world, Hidden ore) {
        Block block = world.getBlockAt(ore.x(), ore.y(), ore.z());
        if (block.getType() != ore.real()) {
            invalidateChunk(world, ore.x(), ore.z());
            return;
        }
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }

    private void invalidateChunk(World world, int blockX, int blockZ) {
        Map<Long, List<Hidden>> chunks = index.get(world);
        if (chunks != null) {
            chunks.remove(packChunk(blockX >> 4, blockZ >> 4));
        }
    }

    private List<Hidden> hiddenIn(World world, int cx, int cz) {
        Map<Long, List<Hidden>> chunks = index.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
        long key = packChunk(cx, cz);
        List<Hidden> cached = chunks.get(key);
        if (cached != null) {
            return cached;
        }
        if (!world.isChunkLoaded(cx, cz)) {
            return List.of();
        }
        List<Hidden> found = scanChunk(world, cx, cz);
        if (chunks.size() > 8192) {
            chunks.clear(); // bounded memory; entries recompute lazily
        }
        chunks.put(key, found);
        return found;
    }

    private List<Hidden> scanChunk(World world, int cx, int cz) {
        int minY = Math.max(config.xrayScanYMin(), world.getMinHeight());
        int maxY = Math.min(config.xrayScanYMax(), world.getMaxHeight() - 1);
        boolean hideExposed = config.xrayHideExposed();
        int baseX = cx << 4;
        int baseZ = cz << 4;
        List<Hidden> found = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    Material type = block.getType();
                    if (isOre(type) && (hideExposed || isHidden(block))) {
                        found.add(new Hidden(baseX + x, y, baseZ + z, type,
                                fakeFor(type).createBlockData()));
                    }
                }
            }
        }
        return List.copyOf(found);
    }
}
